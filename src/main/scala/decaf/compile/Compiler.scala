package decaf.compile

import decaf.compile.codegen.{AssemblyOptimizations, AssemblyReprToStr, AssemblyReprVisitor}
import decaf.compile.cfg.{CfgVisitor, Program}
import decaf.compile.codegen.register.{ColoringRegisterAllocator, NullRegisterAllocator}
import decaf.compile.ir.{DecafIrVisitor, DecafSemanticChecker, Ir, IrProgram}
import decaf.gen.DecafParser.ProgramContext
import decaf.gen.{DecafLexer, DecafParser}
import decaf.util.CommandLineInterface
import org.antlr.v4.runtime._
import decaf.compile.optim.common_subexpression_elmination.OptimCse
import decaf.compile.optim.dead_code_elimination.OptimDeadCode
import decaf.compile.optim.algebraic_simplification.{OptimCfgAlgebraicSimplification, OptimIrAlgebraicSimplification}
import decaf.compile.optim.constant_copy_propagation.OptimCp
import decaf.compile.optim.loop_optim.LoopOptim

import java.io.{BufferedInputStream, PrintStream}
import scala.collection.JavaConversions._

/**
 * Main entry point for the compiler
 */
object Compiler {

  // Optimizer flags
  var OPTIMIZE_ALGEBRAIC_SIMPLIFICATION: Boolean = true
  var OPTIMIZE_CSE: Boolean = true
  var OPTIMIZE_CSP: Boolean = true
  var OPTIMIZE_CP: Boolean = true
  var OPTIMIZE_DCE: Boolean = true
  var OPTIMIZE_NOOP: Boolean = true
  var OPTIMIZE_LOOP_INVARIANT: Boolean = false
  var OPTIMIZE_ASSEMBLY: Boolean = true
  var OPTIMIZE_REG_ALLOC: Boolean = true

  val PRINT_DEBUG: Boolean = false

  /**
   * Error Listener from Antlr so we know if the scan or parse was unsuccessful
   */
  private object AntlrErrorListener extends ConsoleErrorListener {
    private var errored = false;

    override def syntaxError(recognizer: Recognizer[_, _], offendingSymbol: Any, line: Int,
                             charPositionInLine: Int, msg: String, e: RecognitionException): Unit = {
      super.syntaxError(recognizer, offendingSymbol, line, charPositionInLine, msg, e)
      errored = true;
    }

    def resetErrored(): Unit = {
      errored = false;
    }

    def getErrored: Boolean = errored;
  }

  var outFile: PrintStream = _;

  def main(args: Array[String]): Unit = {
    CommandLineInterface.parse(args, Array[String]("asimple", "cse", "cp", "csp", "dce", "noop", "loop", "instsched", "regalloc"))

    if (CommandLineInterface.outfile == null) {
      outFile = Console.out
    } else {
      outFile = new java.io.PrintStream(new java.io.FileOutputStream(CommandLineInterface.outfile))
    }

    OPTIMIZE_ALGEBRAIC_SIMPLIFICATION = CommandLineInterface.opts(0)
    OPTIMIZE_CSE = CommandLineInterface.opts(1)
    OPTIMIZE_CP = CommandLineInterface.opts(2)
    OPTIMIZE_CSP = CommandLineInterface.opts(3)
    OPTIMIZE_NOOP = CommandLineInterface.opts(5)
//    OPTIMIZE_LOOP_INVARIANT = CommandLineInterface.opts(6)
    OPTIMIZE_ASSEMBLY = CommandLineInterface.opts(7)
    OPTIMIZE_REG_ALLOC = CommandLineInterface.opts(8)

    CommandLineInterface.target match {
      case CommandLineInterface.Action.SCAN => {
        scan(CommandLineInterface.infile)
        System.exit(0)
      }
      case CommandLineInterface.Action.INTER => {
        generateIr(CommandLineInterface.infile, print = true)
        System.exit(0)
      }
      case CommandLineInterface.Action.ASSEMBLY => {
        generateAssembly(CommandLineInterface.infile)
        System.exit(0)
      }
      case CommandLineInterface.Action.CFG => {
        generateCfg(CommandLineInterface.infile, print = true)
        System.exit(0)
      }
      case CommandLineInterface.Action.PARSE | CommandLineInterface.Action.DEFAULT | _ => {
        if (parse(CommandLineInterface.infile).isEmpty) {
          System.exit(1)
        }
        System.exit(0)
      }
    }
  }

  /**
   * Scanner, outputs to the outFile
   *
   * @param cs input Character stream
   */
  def scan(cs: CharStream): Unit = {
    val lexer = new DecafLexer(cs)

    val errorListener = AntlrErrorListener
    errorListener.resetErrored()
    lexer.addErrorListener(errorListener)

    val tokens = lexer.getAllTokens.toList
    tokens.foreach(tok => {
      val symbolType = lexer.getVocabulary.getSymbolicName(tok.getType)
      val lineNumber = tok.getLine
      val tokText = tok.getText

      val output: String = symbolType match {
        case "STRING_LITERAL" => s"$lineNumber STRINGLITERAL $tokText"
        case "CHAR_LITERAL" => s"$lineNumber CHARLITERAL $tokText"
        case "BOOL_LITERAL" => s"$lineNumber BOOLEANLITERAL $tokText"
        case "INT_LITERAL" => s"$lineNumber INTLITERAL $tokText"
        case "HEX_LITERAL" => s"$lineNumber INTLITERAL $tokText"
        case "IDENTIFIER" => s"$lineNumber IDENTIFIER $tokText"
        case _ => s"$lineNumber $tokText"
      }
      outFile.println(output)
    })

    if (errorListener.getErrored) {
      System.exit(1);
    } else {
      System.exit(0)
    }
  }

  /**
   * Scanner helper function for reading files and converting them
   * to a character stream for the scan function
   *
   * @param fileName input file name
   */
  private def scan(fileName: String): Unit = {
    val bis: BufferedInputStream = try {
      new BufferedInputStream(new java.io.FileInputStream(fileName))
    } catch {
      case ex: Exception =>
        Console.err.println(CommandLineInterface.infile + " " + ex)
        return
    }
    val inputStream = CharStreams.fromStream(bis)
    scan(inputStream)
    bis.close()
  }

  /**
   * Parse the file to an ANTLR AST
   *
   * @param fileName input file name
   * @return root of the ANTLR AST
   */
  private def parse(fileName: String): Option[ProgramContext] = {
    val bis: BufferedInputStream = try {
      new BufferedInputStream(new java.io.FileInputStream(fileName))
    } catch {
      case ex: Exception =>
        Console.err.println(CommandLineInterface.infile + " " + ex)
        return None
    }

    val inputStream = CharStreams.fromStream(bis)
    val lexer = new DecafLexer(inputStream)
    val tokenStream = new CommonTokenStream(lexer)
    val parser = new DecafParser(tokenStream)
    val errorListener = AntlrErrorListener;
    errorListener.resetErrored()
    parser.addErrorListener(errorListener)

    val tree = parser.program()

    if (errorListener.getErrored) {
      System.exit(2)
      return None
    } else {
      return Some(tree)
    }
  }

  /**
   * Generate the high-level IR tree for the program and perform semantic checks
   *
   * @param fileName input file name
   * @return high level IR
   */
  private def generateIr(fileName: String, print: Boolean): IrProgram = {
    // Use ANTLR to parse the input
    val parseTree: ProgramContext = parse(fileName).get

    // Convert from ANTLR to IR Tree
    DecafIrVisitor.resetErrored()
    var irTree: IrProgram = null

    irTree = DecafIrVisitor.visitProgram(parseTree, removeTernary = false)
    if (DecafIrVisitor.getErrored) {
      System.exit(3)
    }

    // Semantic checker starting with the IR Tree from ANTLR
    if (!DecafSemanticChecker.checkProgram(irTree)) {
      System.exit(4);
    }

    // Remove the ternary for codegen
    irTree = DecafIrVisitor.visitProgram(parseTree, removeTernary = true)

    if (OPTIMIZE_ALGEBRAIC_SIMPLIFICATION){
      irTree = OptimIrAlgebraicSimplification.visitProgram(irTree)
    }

    // Check if the output is Console.out
    if (print) {
      if (outFile == Console.out) {
        pprint.pprintln(irTree, height = Integer.MAX_VALUE)
      } else {
        // This is for exporting to a file
        val prettyPrint: fansi.Str = pprint.apply(irTree, height = Integer.MAX_VALUE)
        outFile.println(prettyPrint.plainText)
      }
    }

    return irTree
  }

  private def generateCfg(fileName: String, print: Boolean): CfgProgram = {
    val program: IrProgram = generateIr(fileName, print = false)
    CfgVisitor.reset()
    var cfg: CfgProgram = CfgVisitor.visitProgram(program)


    var isUpdatedCse = true
    var csePass = 0
    while (isUpdatedCse) {
      isUpdatedCse = false
      csePass += 1

      if (OPTIMIZE_CSE) {
        cfg = OptimCse.visitProgram(cfg)
        isUpdatedCse = isUpdatedCse || OptimCse.UPDATED
      }

      val outputCse = cfg.toString
      if (PRINT_DEBUG) {
        Console.println(s"CSE Pass ${csePass}: ")
        Console.println(outputCse)
        Console.println("----------")
      }

      var isUpdatedPostCse = true
      var postCsePass = 0
      while (isUpdatedPostCse) {
        isUpdatedPostCse = false
        postCsePass += 1

        if (OPTIMIZE_CP || OPTIMIZE_CSP) {
          cfg = OptimCp.visitProgram(cfg)
          isUpdatedPostCse = isUpdatedPostCse || OptimCp.UPDATED
        }
        val outputCp = cfg.toString

        if (OPTIMIZE_ALGEBRAIC_SIMPLIFICATION) {
          cfg = OptimCfgAlgebraicSimplification.visitProgram(cfg)
          isUpdatedPostCse = isUpdatedPostCse || OptimCfgAlgebraicSimplification.UPDATED
        }

        val outputAlgebraic = cfg.toString

        if (PRINT_DEBUG) {
          Console.println(s"Post-CSE Pass ${postCsePass}: ")

          if (OPTIMIZE_CSE && OPTIMIZE_CP) {
            if (outputCse == outputCp) {
              Console.println("CSE -> CP output is identical")
              Console.println("----------")
            }
          }

          Console.println(outputCp)
          Console.println("----------")
          Console.println(outputAlgebraic)
          Console.println("----------")
          Console.println()
        }
      }

      if (OPTIMIZE_DCE) {
        cfg = OptimDeadCode.optimizeProgram(cfg)

        if (PRINT_DEBUG) {
          val outputDce = cfg.toString
          Console.println("DCE Pass:")
          Console.println(outputDce)
        }
      }
    }

    if (OPTIMIZE_LOOP_INVARIANT) {
      cfg = LoopOptim.visitProgram(cfg)
    }

    if (print) {
      println(cfg.toString)
    }

    return cfg
  }

  def generateAssembly(fileName: String): String = {
    val cfg = generateCfg(fileName, print = false)
    var isMac = false;
    if (System.getProperty("os.name").toLowerCase().contains("mac")){
      isMac = true;
    }

    val (assignments, regProgram, funcInfo) = if (OPTIMIZE_REG_ALLOC) ColoringRegisterAllocator.visitProgram(cfg) else
      NullRegisterAllocator.visitProgram(cfg)
    AssemblyReprVisitor.reset()
    var asmProgram = AssemblyReprVisitor.visitProgram(regProgram, isMac, assignments, funcInfo)
    if (OPTIMIZE_ASSEMBLY){
      asmProgram = AssemblyOptimizations.visitProgram(asmProgram)
    }
    val asmString = AssemblyReprToStr.generateProgram(asmProgram)

    outFile.println(asmString)
    return asmString
  }

}
