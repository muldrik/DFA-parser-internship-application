import com.apurebase.arkenv.Arkenv
import com.apurebase.arkenv.argument
import com.apurebase.arkenv.mainArgument
import com.apurebase.arkenv.parse
import java.io.File
import java.lang.StringBuilder


typealias TimeStamp = Long
typealias WhileLoopStack = MutableList<Loop>
data class Assignment(val representation: String="", val timeStamp: TimeStamp = -2)
data class Loop(val marked: MutableSet<Char>, val start: TimeStamp)

val result = mutableListOf<Assignment>()

/**
 * Main data storage for the application
 *
 * Handles iteration through source code and statistics update methods
 *
 * One object is shared between different functions parsing one program
 *
 * @param lines stores source code. Lines have to be split by spaces, operations and delimiters,
 * in this project they are *=()<>+-/
 * @property whileLoopsStack Stores information about current while loops
 * used to handle situations when variable is initialized inside a while loop and
 * later read in the same loop or a different nested while loop but higher in source code
 * @property timestamp is used to indicate when a variable was last read and assigned (according to its line number)
 * and also to mark events such as when the program enters a while loop
 * @property lastWriting stores the timestamp of the last writing for every possible variable
 * @property lastReading stores the timestamp of the last writing for every possible variable
 */
class Tokens(private val lines: List<String>) {
    val whileLoopsStack: WhileLoopStack = mutableListOf()
    private val latestExpressions = MutableList(27) { Assignment() }
    private val lastReading = MutableList<TimeStamp>(27) { -1 }
    private val lastWriting = MutableList<TimeStamp>(27) { -2 }
    private var timestamp: TimeStamp = 0

    fun getNewTimestamp(): TimeStamp = timestamp++

    fun updateWriting(varName: Char, assignmentRepresentation: String) {
        val id = varName-'a'
        if (lastReading[id] < lastWriting[id]) {
            result.add(latestExpressions[id])
        }
        val time = getNewTimestamp()
        lastWriting[id] = time
        latestExpressions[id] = Assignment(assignmentRepresentation, time)
    }

    fun updateReading(varName: Char) {
        val id = varName-'a'
        if (whileLoopsStack.isNotEmpty() && lastWriting[id] < whileLoopsStack.last().start)
            whileLoopsStack.last().marked.add(varName)
        lastReading[id] = getNewTimestamp()
    }
    private var chunkIndex = 0

    fun nextWord(peek: Int = 0): String {
        return if (chunkIndex + peek >= lines.size) ""
        else lines[chunkIndex + peek]
    }

    fun advance() {
        chunkIndex++
    }

    val operators = listOf('+', '-', '*', '/', '<', '>')
}

/**
 * Checks if a string is a valid variable name
 */
fun String.isVariable() = (length == 1 && (first() - 'a' >= 0) && (first() - 'a' < 26))


/**
 * Parses a program stored in
 * @param tokens, finds unused assignments and saves the results in a global variable
 */
fun parseProgram(tokens: Tokens) {
    val current = tokens.nextWord()
    if (current.isEmpty())
        return
    else {
        parseStatementList(tokens)
    }
    for (c in 'a'..'z') {
        tokens.updateWriting(c, "")
    }
}

/**
 * Can be optionally changed to distinct boolean expression from integer
 */
fun parseCondition(tokens: Tokens) = parseExpression(tokens)


/**
 * Stops parses a statement list according to language syntax
 */
fun parseStatementList(tokens: Tokens) {
    if (tokens.nextWord() == "end" || tokens.nextWord().isEmpty()) {
        tokens.advance()
        return
    }
    parseStatement(tokens)
    parseStatementList(tokens)
}


/**
 * Parses an expression according to language syntax
 * Return a string representation to later be used to output the program result
 */
fun parseExpression(tokens: Tokens): String {
    val representation = StringBuilder()
    val word = tokens.nextWord()
    when {
        word == "(" -> {
            representation.append('(')
            tokens.advance()
            representation.append(parseExpression(tokens))
            if (tokens.nextWord().isEmpty() || tokens.nextWord().first() != ')')
                throw IllegalArgumentException("Expected ')'")
            representation.append(')')
        }
        word.toIntOrNull() != null -> {
            val constant = word.toInt()
            representation.append(constant)
        }
        word=="-" -> {
            tokens.advance()
            val constant = tokens.nextWord().toIntOrNull()
                ?: throw IllegalArgumentException("Integer expected after minus sign")
            representation.append("-$constant")
        }
        word.isVariable() -> {
            val varName = word.first()
            tokens.updateReading(varName)
            representation.append(word)
        }
        else -> throw IllegalArgumentException("Unexpected operation: '$word'. Constant or variable expected")
    }

    tokens.advance()
    if (tokens.nextWord().isNotEmpty() && tokens.nextWord().first() in tokens.operators) {
        representation.append(" ${tokens.nextWord()} ")
        tokens.advance()
        representation.append(parseExpression(tokens))
    }
    return representation.toString()
}


/**
 * Parses a statement according to language syntax
 * If a statement is an assignment, checks whether last assignment has ever been used,
 * if not, then adds it to the result
 */
fun parseStatement(tokens: Tokens) {
    val word = tokens.nextWord()
    when {
        (word.isVariable()) -> {
            val newVar = word.first()
            tokens.advance()
            assert(tokens.nextWord() == "=")
            tokens.advance()
            val expr = parseExpression(tokens)
            tokens.updateWriting(newVar, "$word = $expr")
        }
        word == "if" -> {
            tokens.advance()
            parseCondition(tokens)
            parseStatementList(tokens)
        }
        word == "while" -> {
            tokens.advance()
            parseCondition(tokens)
            val localUsedVariables = mutableSetOf<Char>()
            tokens.whileLoopsStack.add(Loop(localUsedVariables, tokens.getNewTimestamp()))
            parseStatementList(tokens)
            tokens.whileLoopsStack.last().marked.forEach {
                tokens.updateReading(it)
            }
            tokens.whileLoopsStack.removeLast()
        }
        else -> throw IllegalArgumentException("Unable to parse statement: '$word'. " +
                "Variable name or condition keyword expected")

    }
}

class Parameters : Arkenv() {

    val input: File by mainArgument {
        mapping = { File(it) }
        description = "Path to a text file containing the program"
        validate("File does not exist or cannot be read") {
            it.exists() && it.isFile && it.canRead()
        }
    }

    val output: String? by argument("--output") {
        description = "Report output file"
        defaultValue = null
    }

}

lateinit var parameters: Parameters

fun main(args: Array<String>) {
    parameters = Parameters().parse(args)
    val delimiterFilter = Regex("(?<=[=()<>/*+-])|(?=[=()<>/*+-])|(\\s+)")

    val input = parameters.input
        .readLines()
        .joinToString(separator = " ")
        .split(delimiterFilter)
        .map { it.trim() }
        .filter { it.isNotEmpty() }
    parseProgram(Tokens(input))
    result.sortBy { it.timeStamp }

    val outputFileName = parameters.output
    if (outputFileName == null) {
        result.forEach {
            println(it.representation)
        }
    }
    else {
        val outputWriter = File(outputFileName).writer()
        result.forEach {
            outputWriter.write("${it.representation}\n")
        }
        outputWriter.close()
    }
}