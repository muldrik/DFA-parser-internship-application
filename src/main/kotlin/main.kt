import com.apurebase.arkenv.Arkenv
import com.apurebase.arkenv.argument
import com.apurebase.arkenv.mainArgument
import com.apurebase.arkenv.parse
import java.io.File
import java.lang.StringBuilder

typealias TimeStamp = Long
data class Assignment(val representation: String="", val timeStamp: TimeStamp = -2)

val result = mutableListOf<Assignment>()

data class Loop(val marked: MutableSet<Char>, val start: TimeStamp)

typealias WhileLoopStack = MutableList<Loop>

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

fun String.isVariable() = (length == 1 && (first() - 'a' >= 0) && (first() - 'a' < 26))

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

fun parseCondition(tokens: Tokens) = parseExpression(tokens)

fun parseStatementList(tokens: Tokens) {
    if (tokens.nextWord() == "end" || tokens.nextWord().isEmpty()) {
        tokens.advance()
        return
    }
    parseStatement(tokens)
    parseStatementList(tokens)
}

fun parseExpression(tokens: Tokens): String {
    val representation = StringBuilder()
    val kek = tokens.nextWord()
    if (kek == "("){
        representation.append('(')
        tokens.advance()
        representation.append(parseExpression(tokens))
        if (tokens.nextWord().isEmpty() || tokens.nextWord().first() != ')')
            throw IllegalArgumentException("Expected ')'")
        representation.append(')')
    }
    else if (kek.toIntOrNull() != null) {
        val constant = kek.toInt()
        representation.append(constant)
    }
    else if (kek.isVariable()) {
        val varName = kek.first()
        tokens.updateReading(varName)
        representation.append(kek)
    }
    else throw IllegalArgumentException("Unexpected operation: '$kek'. Constant or variable expected")
    tokens.advance()
    if (tokens.nextWord().isNotEmpty() && tokens.nextWord().first() in tokens.operators) {
        representation.append(" ${tokens.nextWord()} ")
        tokens.advance()
        representation.append(parseExpression(tokens))
    }
    return representation.toString()
}

fun parseStatement(tokens: Tokens) {
    val word = tokens.nextWord()
    if (word.isVariable()) {
        val newVar = word.first()
        tokens.advance()
        assert(tokens.nextWord() == "=")
        tokens.advance()
        val expr = parseExpression(tokens)
        tokens.updateWriting(newVar, "$word = $expr")
    }
    else if (word == "if") {
        tokens.advance()
        parseCondition(tokens)
        parseStatementList(tokens)
    }
    else if (word == "while") {
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
    else throw IllegalArgumentException("Unable to parse statement: '$word'. Variable name or condition keyword expected")
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