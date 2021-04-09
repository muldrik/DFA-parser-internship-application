import org.junit.jupiter.api.*
import java.io.File
import java.lang.IllegalArgumentException
import java.nio.file.Paths
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith

class AppTest {
    companion object {
        private const val TEST_DATA_PATH = "src/test/resources"
        private const val TEMPORARY_DIRECTORY = "temp_test_data"

        @BeforeAll
        @JvmStatic
        fun createArchives() {
            val testRoot = File(TEMPORARY_DIRECTORY)
            if (!testRoot.exists()){
                testRoot.mkdirs()
            }
        }

        @AfterAll
        @JvmStatic
        fun cleanUp() {
            File(TEMPORARY_DIRECTORY).deleteRecursively()
        }
    }

    @BeforeEach
    fun clear() {
        result.clear()
    }

    @Test
    fun `task example`() {
        testInputs("example.txt")
    }

    @Test
    fun `simple while usage trap`() {
        testInputs("simpleWhileTrap")
    }

    @Test
    fun `invalid input`() {
        assertFails() {
            testInputs("incorrect")
        }
    }

    @Test
    fun `unexpected float`() {
        assertFailsWith<IllegalArgumentException>("Unexpected operation: '10.0'. Constant or variable expected") {
            testInputs("invalidConstant")
        }
    }

    @Test
    fun `densely nested if`() {
        testInputs("nestedIf")
    }

    private fun testInputs(inputFile: String) {
        val outputFileName = "$inputFile.actual"

        val args = arrayOf(
            "--threads", inputFile.relativeToTestDir(),
            "--output", "$inputFile.actual".relativeToTemporaryDir()
        )
        main(args)
        val expectedFileName = "$inputFile.expected"
        assertFilesHaveSameContent(expectedFileName, outputFileName)
    }

    private fun assertFilesHaveSameContent(expectedFileName: String, actualFileName: String, message: String? = null) {
        val actual = Paths.get(TEMPORARY_DIRECTORY).resolve(actualFileName).toFile().readText()
        val expected = Paths.get(TEST_DATA_PATH).resolve(expectedFileName).toFile().readText()
        assertEquals(expected, actual, message)
    }

    private fun String.relativeToTemporaryDir(): String = Paths.get(TEMPORARY_DIRECTORY).resolve(this).toString()
    private fun String.relativeToTestDir(): String = Paths.get(TEST_DATA_PATH).resolve(this).toString()
}