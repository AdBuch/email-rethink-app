package ch.emailrethink.transport

data class MailFolder(val name: String, val delimiter: String? = null)
data class MailHeader(val uid: Long, val from: String?, val subject: String?, val date: String?)

interface MailTransport {
    suspend fun connect(host: String, port: Int, username: String, secret: String)
    suspend fun listFolders(): List<MailFolder>
    suspend fun fetchHeaders(folder: String, limit: Int = 50): List<MailHeader>
    suspend fun disconnect()
}
