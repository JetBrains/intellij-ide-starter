import org.junit.jupiter.api.extension.BeforeTestExecutionCallback
import org.junit.jupiter.api.extension.AfterTestExecutionCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.ExtensionContext.Namespace
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class TestDurationExtension : BeforeTestExecutionCallback, AfterTestExecutionCallback {
    companion object {
        private val namespace = Namespace.create("TestDuration")
        private val logFilePath = "out/test_duration_log.txt" // You can change the path as needed
    }

    override fun beforeTestExecution(context: ExtensionContext) {
        context.getStore(namespace).put("startTime", System.currentTimeMillis())
    }

    override fun afterTestExecution(context: ExtensionContext) {
        val startTime = context.getStore(namespace).get("startTime", Long::class.java)
        val endTime = System.currentTimeMillis()
        val duration = endTime - startTime

        val logEntry = "Test '${context.requiredTestClass.simpleName}.${context.requiredTestMethod.name}.${context.displayName}' took $duration ms to execute at ${getCurrentTime()}.\n"

        try {
            FileWriter(logFilePath, true).use { writer ->
                writer.write(logEntry)
            }
        } catch (e: IOException) {
            e.printStackTrace() // Handle exception
        }

        println(logEntry) // Optional: also print to console
    }

    private fun getCurrentTime(): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
        return dateFormat.format(Date())
    }
}
