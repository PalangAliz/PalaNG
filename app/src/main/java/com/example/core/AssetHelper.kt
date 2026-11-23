package com.example.core

import android.content.Context
import android.system.Os
import android.system.OsConstants
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileDescriptor
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

private object Libv2rayBridge {
    fun initCoreEnv(assetPath: String) {
        libv2ray.Libv2ray.initCoreEnv(assetPath, "")
    }

    fun checkVersionX(): String {
        return libv2ray.Libv2ray.checkVersionX()
    }

    fun measureOutboundDelay(configJson: String, testUrl: String): Long {
        return libv2ray.Libv2ray.measureOutboundDelay(configJson, testUrl)
    }
}

data class GeoAssetInfo(
    val fileName: String,
    val exists: Boolean,
    val sizeBytes: Long,
    val lastModified: Long
)

object AssetHelper {
    private val initialized = AtomicBoolean(false)
    private val initLock = Any()
    val geoFiles = listOf("geoip.dat", "geosite.dat", "geoip-only-cn-private.dat")

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    fun ensureCoreAssets(context: Context, forceOverwrite: Boolean = false) {
        initCoreEnvOnce(context)
        val targetDir = context.filesDir
        geoFiles.forEach { fileName ->
            val outFile = File(targetDir, fileName)
            if (forceOverwrite || !outFile.exists() || outFile.length() == 0L) {
                runCatching {
                    context.assets.open(fileName).use { input ->
                        FileOutputStream(outFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }
        }
    }

    fun initCoreEnvOnce(context: Context) {
        if (initialized.get()) return
        synchronized(initLock) {
            if (initialized.get()) return
            val savedOut = runCatching { Os.dup(FileDescriptor.out) }.getOrNull()
            val savedErr = runCatching { Os.dup(FileDescriptor.err) }.getOrNull()
            try {
                runCatching {
                    Libv2rayBridge.initCoreEnv(context.filesDir.absolutePath)
                }
            } finally {
                if (savedOut != null) {
                    runCatching { Os.dup2(savedOut, OsConstants.STDOUT_FILENO) }
                    runCatching { Os.close(savedOut) }
                }
                if (savedErr != null) {
                    runCatching { Os.dup2(savedErr, OsConstants.STDERR_FILENO) }
                    runCatching { Os.close(savedErr) }
                }
                initialized.set(true)
            }
        }
    }

    fun getCoreVersion(context: Context? = null): String {
        if (context != null) initCoreEnvOnce(context)
        return runCatching { Libv2rayBridge.checkVersionX() }.getOrElse { "Xray-core (libv2ray)" }
    }

    fun measureOutboundDelay(context: Context, configJson: String, testUrl: String): Long {
        initCoreEnvOnce(context)
        return runCatching {
            Libv2rayBridge.measureOutboundDelay(configJson, testUrl)
        }.getOrElse { -1L }
    }

    fun hasGeoIp(context: Context): Boolean {
        val f = File(context.filesDir, "geoip.dat")
        return f.exists() && f.length() > 1024L
    }

    fun hasGeoSite(context: Context): Boolean {
        val f = File(context.filesDir, "geosite.dat")
        return f.exists() && f.length() > 1024L
    }

    fun getAssetInfos(context: Context): List<GeoAssetInfo> {
        val dir = context.filesDir
        return geoFiles.map { name ->
            val f = File(dir, name)
            GeoAssetInfo(
                fileName = name,
                exists = f.exists(),
                sizeBytes = if (f.exists()) f.length() else 0L,
                lastModified = if (f.exists()) f.lastModified() else 0L
            )
        }
    }

    fun downloadAsset(context: Context, fileName: String): Result<Long> {
        val url = when (fileName) {
            "geoip.dat" -> "https://github.com/Loyalsoldier/v2ray-rules-dat/releases/latest/download/geoip.dat"
            "geosite.dat" -> "https://github.com/Loyalsoldier/v2ray-rules-dat/releases/latest/download/geosite.dat"
            "geoip-only-cn-private.dat" -> "https://github.com/Loyalsoldier/geoip/releases/latest/download/geoip-only-cn-private.dat"
            else -> return Result.failure(IllegalArgumentException("Unknown asset: $fileName"))
        }
        return runCatching {
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("HTTP ${response.code}")
                val body = response.body ?: error("Empty response body")
                val tempFile = File(context.filesDir, "$fileName.tmp")
                val targetFile = File(context.filesDir, fileName)
                body.byteStream().use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }
                if (tempFile.length() < 1024) {
                    tempFile.delete()
                    error("Downloaded file too small")
                }
                if (targetFile.exists()) targetFile.delete()
                tempFile.renameTo(targetFile)
                targetFile.length()
            }
        }
    }
}
