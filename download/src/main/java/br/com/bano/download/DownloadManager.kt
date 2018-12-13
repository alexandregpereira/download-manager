package br.com.bano.download

import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import br.com.bano.tls12socketfactory.Tls12SocketFactory
import java.io.*
import java.net.URL
import java.security.KeyStore
import java.util.*
import javax.net.ssl.*

open class DownloadManager : HandlerThread("DownloadManager") {

    private val mDownloadPool = HashSet<DownloadData>()
    private val mDownloadPoolLock = Object()
    private var mWorkerHandler: Handler? = null

    fun download(downloadData: DownloadData) {
        if(!isAlive) {
            start()
            prepareHandler()
        }

        val file = File(downloadData.encryptFilePath)
        if(file.exists()) file.delete()
        execute(downloadData)
    }

    private fun execute(downloadData: DownloadData) {
        val workerHandler = mWorkerHandler ?: return
        synchronized(mDownloadPoolLock) {
            mDownloadPool.add(downloadData)
        }
        workerHandler.post {
            val downloaded = downloadFile(downloadData)

            synchronized(mDownloadPoolLock) {
                mDownloadPool.remove(downloadData)
            }

            if(downloaded) {
                onDownloaded(downloadData)
            }
        }
    }

    protected open fun onDownloaded(downloadData: DownloadData) {
        downloadData.handler.post { downloadData.onDownloadFinish?.invoke(downloadData) }
    }

    fun cancelAll(): Set<DownloadData> {
        synchronized(mDownloadPoolLock) {
            val pool = HashSet<DownloadData>(mDownloadPool)
            mDownloadPool.clear()
            return pool
        }
    }

    fun cancel(filePath: String) {
        if(isFinished()) return
        synchronized(mDownloadPoolLock) {
            val downloadData = mDownloadPool.find { it.encryptFilePath == filePath } ?: return
            mDownloadPool.remove(downloadData)
        }
    }

    private fun isInThePool(downloadData: DownloadData): Boolean {
        synchronized(mDownloadPoolLock) {
            return mDownloadPool.contains(downloadData)
        }
    }

    fun isFinished(): Boolean =
        synchronized(mDownloadPoolLock) {
            mDownloadPool.isEmpty()
        }

    private fun downloadFile(downloadData: DownloadData): Boolean {
        try {
            Log.d("SecurityDownloadManager", "download init: $downloadData")
            val url = URL(downloadData.url)
            val connection: HttpsURLConnection = url.openConnection() as HttpsURLConnection
            if (Build.VERSION.SDK_INT == 19) {
                Log.d("SecurityDownloadManager", "Tls12SocketFactory enable")
                val sc = SSLContext.getInstance("TLSv1.2")
                sc.init(null, getTrustManagers(), null)
                connection.sslSocketFactory = Tls12SocketFactory(sc.socketFactory)
            }
            if(downloadData.headers != null) {
                for((key, value) in downloadData.headers) {
                    connection.setRequestProperty(key, value)
                }
            }

            connection.connect()
            // this will be useful so that you can show a typical 0-100% progress bar
            val fileLength = connection.contentLength

            // download t he file
            val input = BufferedInputStream(connection.inputStream)
            val output = FileOutputStream(downloadData.encryptFilePath)

            val data = ByteArray(1024)
            var total: Long = 0
            var count: Int = input.read(data)
            var previousProgress = 0
            while (count != -1) {
                if(!isInThePool(downloadData)) {
                    File(downloadData.encryptFilePath).delete()
                    return false
                }
                total += count.toLong()
                val progress = (total.toFloat() * 100 / fileLength).toInt()
                if(previousProgress != progress) downloadData.handler.post {
                    downloadData.onProgressUpdate?.invoke(progress, downloadData)
                }
                output.write(data, 0, count)
                count = input.read(data)
                previousProgress = progress
            }

            output.flush()
            output.close()
            input.close()
            Log.d("SecurityDownloadManager", "download finish")
            return true
        } catch (e: IOException) {
            e.printStackTrace()
            Log.e("SecurityDownloadManager", "download error")
            File(downloadData.encryptFilePath).delete()
            sendResponseError(downloadData, e)
            return false
        }
    }

    private fun getTrustManagers(): Array<TrustManager> {
        val trustManagerFactory = TrustManagerFactory.getInstance(
            TrustManagerFactory.getDefaultAlgorithm())
        trustManagerFactory.init(null as KeyStore?)
        val trustManagers = trustManagerFactory.trustManagers
        if (trustManagers.size != 1 || trustManagers[0] !is X509TrustManager) {
            throw IllegalStateException("Unexpected default trust managers:" + Arrays.toString(trustManagers))
        }
        return trustManagers
    }

    private fun sendResponseError(downloadData: DownloadData, e: IOException) {
        val response = if(e is FileNotFoundException) {
            DownloadData.HTTP_NOT_AUTHORIZED_RESPONSE
        }
        else DownloadData.UNKNOWN_ERROR_RESPONSE
        downloadData.handler.post { downloadData.onDownloadError?.invoke(response, downloadData) }
    }

    private fun prepareHandler() {
        mWorkerHandler = Handler(looper)
    }


}