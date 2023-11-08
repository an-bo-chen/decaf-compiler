# Decaf Compiler

### Infrastructure
This project uses Scala 2.11.12, and Scala's preferred build system, [Scala Build Tool](https://www.scala-sbt.org/) (sbt).

The lexer/parser generator that we are using is [ANTLR 4](https://www.antlr.org/). 

The lexer grammar is located in [DecafLexer.g4](https://github.com/6035-sp23/alexdang-anbochen-youran-sophiez/blob/main/src/main/java/decaf/gen/DecafLexer.g4).

The parser grammar is located in [DecafParser.g4](https://github.com/6035-sp23/alexdang-anbochen-youran-sophiez/blob/main/src/main/java/decaf/gen/DecafParser.g4).

The IR generator is located in [DecafIrVisitor.scala](https://github.com/6035-sp23/alexdang-anbochen-youran-sophiez/blob/main/src/main/scala/decaf/compile/ir/DecafIrVisitor.scala).

The IR semantic checker is located in [DecafSemanticChecker.scala](https://github.com/6035-sp23/alexdang-anbochen-youran-sophiez/blob/main/src/main/scala/decaf/compile/ir/DecafSemanticChecker.scala).

The main CFG generator is located in [CfgVisitor.scala](https://github.com/6035-sp23/alexdang-anbochen-youran-sophiez/blob/main/src/main/scala/decaf/compile/cfg/CfgVisitor.scala) 

The main x86 code generator is located in [CfgCodegen.scala](https://github.com/6035-sp23/alexdang-anbochen-youran-sophiez/blob/main/src/main/scala/decaf/compile/asm/CfgCodegen.scala)

### Directory Structure
```
.
├── .idea                   IntelliJ IDEA project files
├── lib                     Libraries (ANTLR4 and ScalaTest)
├── src/main/      
│   ├── java                Java sources
│   │   └── decaf/gen       Generated sources (Java)
│   │   └── decaf/*.g4      Lexer and Parser Grammars
│   └── scala               Scala sources
│   │   └── decaf.compile
│   │   │   └── asm         Assembly generation
│   │   │   └── cfg         Control flow graph generation
│   │   │   └── ir          IR generation
│   │   │   └── optim       Optimizers
│   │   │   Compiler.scala  Main program entry point
├── tests                   Tests from the public_tests repository
├── build.sbt               Scala Built Tool script
├── build.sh                Build script for autograder
└── run.sh                  Run script for autograder
```
