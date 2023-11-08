import org.scalatest.funsuite.AnyFunSuite

import java.nio.file.{FileSystems, Files, Path}
import scala.collection.JavaConverters.{asScalaBufferConverter, asScalaIteratorConverter}
import decaf.compile.Compiler

import scala.io.Source
import scala.sys.process._

class Codegen_Integration extends AnyFunSuite {

  val TESTS_DIR_INPUT: String = "./tests/codegen/input"
  val TESTS_DIR_OUTPUT: String = "./tests/codegen/output"
  val TESTS_DIR_ERROR: String = "./tests/codegen/error"

  val TESTS_DIR_TMP: String = "./tests/tmp/"

  val SHOULD_PRINT_RESULT: Boolean = true

  val dir: Path = FileSystems.getDefault.getPath(TESTS_DIR_INPUT)
  Files.walk(dir).iterator().asScala.toList
    .filter(Files.isRegularFile(_))
    .sortBy(p => p.getFileName.toString)
    .foreach(p => {
      test(s"test ${p.getFileName.toString}") {
        val decafFileOut = s"${TESTS_DIR_TMP}/${p.getFileName.toString}.s"
        val decafFileOutPath = FileSystems.getDefault.getPath(decafFileOut)
        val decafTmpFolderPath = FileSystems.getDefault.getPath(TESTS_DIR_TMP)

        Compiler.outFile = new java.io.PrintStream(new java.io.FileOutputStream(decafFileOut))
        val outputAssembly = Compiler.generateAssembly(p.toAbsolutePath.toString)

        val programName = p.getFileName.toString.replace(".dcf", "")
        val programPath = FileSystems.getDefault.getPath(s"${TESTS_DIR_TMP}/${programName}")
        val compileCmd = s"gcc -O0 ${decafFileOutPath.toAbsolutePath.toString} -o ${decafTmpFolderPath.toAbsolutePath.toString}/${programName}"
        val executeCmd = s"${programPath.toAbsolutePath.toString}"

        val compileResult = compileCmd.!!

        val beforeTime = System.nanoTime()
        val executeResult = executeCmd.!!
        val afterTime = System.nanoTime()

        val difference = (afterTime - beforeTime) / 1000000L

        Console.println("Test Result for " + p.getFileName.toString)
        Console.println("Time: " + difference + " ms")

        var linesOfAssembly = 0
        outputAssembly.strip().lines().toList.asScala.foreach(line => {
          if (line.strip() != "" && !line.startsWith("//")){
            linesOfAssembly += 1
          }
        })
        Console.println("Lines of Assembly: " + linesOfAssembly)
        Console.println()

        if (SHOULD_PRINT_RESULT){
          Console.println(executeResult)
        }

        val outputSource = Source.fromFile(TESTS_DIR_OUTPUT + "/" + p.getFileName.toString + ".out")
        val fileContents = outputSource.iter.mkString
        outputSource.close()

        if (fileContents.strip() == "*"){
          assert {
            executeResult.strip().nonEmpty
          }
        } else {
          assert {
            fileContents.strip() == executeResult.strip()
          }
        }
      }
    })

  Files.walk(FileSystems.getDefault.getPath(TESTS_DIR_ERROR)).iterator().asScala.toList
    .filter(Files.isRegularFile(_))
    .sortBy(p => p.getFileName.toString)
    .foreach(p => {
      if (!p.getFileName.toString.contains(".out")){
        test(s"error test ${p.getFileName.toString}") {
          val decafFileOut = s"${TESTS_DIR_TMP}/${p.getFileName.toString}.s"
          val decafFileOutPath = FileSystems.getDefault.getPath(decafFileOut)
          val decafTmpFolderPath = FileSystems.getDefault.getPath(TESTS_DIR_TMP)

          Compiler.outFile = new java.io.PrintStream(new java.io.FileOutputStream(decafFileOut))
          Compiler.generateAssembly(p.toAbsolutePath.toString)

          val programName = p.getFileName.toString.replace(".dcf", "")
          val programPath = FileSystems.getDefault.getPath(s"${TESTS_DIR_TMP}/${programName}")
          val compileCmd = s"gcc -O0 ${decafFileOutPath.toAbsolutePath.toString} -o ${decafTmpFolderPath.toAbsolutePath.toString}/${programName}"
          val executeCmd = s"${programPath.toAbsolutePath.toString}"

          val compileResult = compileCmd.!!
          val executeResult = executeCmd.!

          if (SHOULD_PRINT_RESULT) {
            Console.println(executeResult)
          }

          assert {
            executeResult != 0
          }
        }
      }
    })


}