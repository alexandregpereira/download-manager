package br.com.bano.download
import android.os.Handler
import android.os.Looper

/**
 * Created by bk_alexandre.pereira on 07/09/2017.
 *
 */
open class DownloadData(threadLooper: Looper,
                        val url: String,
                        val headers: HashMap<String, String>? = null,
                        val encryptFilePath: String,
                        val objStored: Any? = null,
                        val onProgressUpdate: ((progress: Int, DownloadData) -> Unit)? = null,
                        val onDownloadError: ((response: Int, DownloadData) -> Unit)? = null,
                        val onDownloadFinish: ((DownloadData) -> Unit)? = null) {

    val handler = Handler(threadLooper)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DownloadData

        if (encryptFilePath != other.encryptFilePath) return false

        return true
    }

    override fun hashCode(): Int = encryptFilePath.hashCode()

    override fun toString(): String = objStored.toString()

    companion object {
        const val HTTP_NOT_AUTHORIZED_RESPONSE = 404
        const val UNKNOWN_ERROR_RESPONSE = 500
    }
}