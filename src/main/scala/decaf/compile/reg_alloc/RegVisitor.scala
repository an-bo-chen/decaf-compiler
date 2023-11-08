package decaf.compile.reg_alloc

import decaf.compile._
import decaf.compile.cfg._
import decaf.compile.reg_alloc.types._
import decaf.compile.codegen.{REG, RegisterDefinitions}
import scala.collection.mutable
object RegVisitor {
  var USABLE_REGISTERS: Set[REG] = Set(
    RegisterDefinitions.RDI,
    RegisterDefinitions.RSI,
    RegisterDefinitions.RDX,
    RegisterDefinitions.RCX,
    RegisterDefinitions.R8,
    RegisterDefinitions.R9,
    RegisterDefinitions.RBX,
    RegisterDefinitions.R12,
    RegisterDefinitions.R13,
    RegisterDefinitions.R14,
    RegisterDefinitions.R15
  )

  var VIRTUAL_TO_REG: Map[VirtualRegister, RealRegister] = USABLE_REGISTERS.map(reg => getNewVirtualReg() -> RealRegister(reg)).toMap

  var virtualRegCount = 0
  def getNewVirtualReg(): VirtualRegister = {
    virtualRegCount += 1
    VirtualRegister(virtualRegCount)
  }

  /**
   * Register Allocation
   *
   * @param program the CfgProgram
   * @return Tuple2 containing a RepProgram, a mapping from each function to a set of used registers and a set of spilled webs
   */
  def visitProgram(program: CfgProgram, registers: Option[Set[REG]]): (RegProgram, Map[String, (Set[RealRegister], Map[CfgVariable, RealRegister], Set[CfgVariable])]) = {
    virtualRegCount = 0
    if (registers.isEmpty){
      USABLE_REGISTERS = Set(
        RegisterDefinitions.RDI,
        RegisterDefinitions.RSI,
        RegisterDefinitions.RDX,
        RegisterDefinitions.RCX,
        RegisterDefinitions.R8,
        RegisterDefinitions.R9,
        RegisterDefinitions.RBX,
        RegisterDefinitions.R12,
        RegisterDefinitions.R13,
        RegisterDefinitions.R14,
        RegisterDefinitions.R15
      )
    } else {
      USABLE_REGISTERS = registers.get
    }
    VIRTUAL_TO_REG = USABLE_REGISTERS.map(reg => getNewVirtualReg() -> RealRegister(reg)).toMap

    val funcToRegInfo = mutable.Map.empty[String, (Set[RealRegister], Map[CfgVariable, RealRegister], Set[CfgVariable])]

    (program.copy(functions = program.functions.map { func =>
      val (scope, registers, varToReg, spilled) = visitScope(func.scope, program.globalVariables.map(decl => decl.variable).toSet)
      funcToRegInfo.update(func.identifier, (registers, varToReg, spilled))

      func.copy(
        scope = scope
      )
    }),
    funcToRegInfo.toMap
    )
  }

  /**
   *
   * @param scope the CfgScope of a given function
   * @param globalVariables set of global variables
   * @return Tuple3 containing a RegScope, set of used registers, and set of spilled webs
   */
  private def visitScope(scope: CfgScope, globalVariables: Set[CfgVariable]): (RegScope, Set[RealRegister], Map[CfgVariable, RealRegister], Set[CfgVariable]) = {
    val livenessScope = ScopeLiveness.getScopeLiveness(scope, globalVariables)
    val interferenceGraph = InterferenceGraph.buildGraph(livenessScope)
    val (varToReg, spilled) = InterferenceGraph.colorGraph(interferenceGraph, VIRTUAL_TO_REG.keySet)

    val regBasicBlocks = scope.basicBlocks.map({
      case (label, block) =>
        (label, visitBlock(block, varToReg))
    })

    val varToRealReg: Map[CfgVariable, RealRegister] = varToReg.map(
      pair => {
        val (variable, virtualRegister) = pair
        (variable, VIRTUAL_TO_REG(virtualRegister))
      }
    )

    (scope.copy(
      basicBlocks = regBasicBlocks
    ),
    varToReg.values.map(VIRTUAL_TO_REG).toSet,
    varToRealReg,
    spilled
    )
  }

  private def visitBlock(block: CfgBasicBlock, varToReg: Map[CfgVariable, VirtualRegister]): RegBasicBlock = {
    block match {
      case RegularBlock(fieldDecls, statements) =>
        RegRegularBlock(
          fieldDecls = fieldDecls,
          statements = statements.map(statement => visitStatement(statement, varToReg))
        )
      case ConditionalBlock(fieldDecls, statements, condition) =>
        val variables = mutable.ListBuffer.empty[CfgVariable]
        variables ++= getExprVars(condition)

        RegConditionalBlock(
          fieldDecls = fieldDecls,
          statements = statements.map(statement => visitStatement(statement, varToReg)),
          condition = (condition, getLocationMap(variables.toList, varToReg))
        )
    }
  }

  private def getLocationMap(variables: List[CfgVariable], varToReg: Map[CfgVariable, VirtualRegister]): Map[CfgVariable, RealRegister] = {
    val locationMap = mutable.Map.empty[CfgVariable, RealRegister]

    variables.foreach(variable =>
      if (varToReg.contains(variable)) {
        locationMap.update(variable, VIRTUAL_TO_REG(varToReg(variable)))
      }
    )
    locationMap.toMap
  }
  private def visitStatement(statement: CfgStatement, varToReg: Map[CfgVariable, VirtualRegister]): RegStatement[RealRegister] = {
    val variables = mutable.ListBuffer.empty[CfgVariable]

    statement match {
      case statement: CfgAssignStatement =>
        statement match {
          case cfgRegAssignmentStatement@CfgRegAssignStatement(to, value) =>
            to.index match {
              case Some(x) =>
                variables ++= getExprVars(x)
              case None =>
                variables += to.variable
            }

            variables ++= getExprVars(value)
            RegAssignStatement[RealRegister](
              locationMap = getLocationMap(variables.toList, varToReg),
              origStatement = cfgRegAssignmentStatement
            )
          case cfgArrayAssignStatement: CfgArrayAssignStatement =>
            RegArrayAssignStatement[RealRegister](cfgArrayAssignStatement)
        }
      case functionCallStatement@CfgFunctionCallStatement(_, parameters) =>
        parameters.foreach({
          case CfgFunctionCallValueParam(param) =>
            variables ++= getExprVars(param)
          case CfgFunctionCallStringParam(_) =>
        })

        RegFunctionCallStatement[RealRegister](
          locationMap = getLocationMap(variables.toList, varToReg),
          origStatement = functionCallStatement
        )
      case cfgReturnStatement@CfgReturnStatement(value) =>
        value match {
          case Some(x) => variables ++= getExprVars(x)
          case None =>
        }

        RegReturnStatement[RealRegister](
          locationMap = getLocationMap(variables.toList, varToReg),
          origStatement = cfgReturnStatement
        )
    }
  }

  private def getExprVars(expr: CfgExpression): Set[CfgVariable] = {
    val variables = mutable.ListBuffer.empty[CfgVariable]

    def _processCfgValue(value: CfgValue): Unit = {
      value match {
        case CfgImmediate(_) =>
        case variable: CfgVariable => variables += variable
      }
    }

    expr match {
      case value: CfgValue =>
        _processCfgValue(value)
      case CfgArrayReadExpr(_, index) =>
        _processCfgValue(index)
      case CfgLenExpr(_) =>
      case CfgFunctionCallExpr(_, params) =>
        params.foreach {
          case CfgFunctionCallValueParam(param) => _processCfgValue(param)
          case CfgFunctionCallStringParam(_) =>
        }
      case CfgUnaryOpExpr(_, expr) =>
        _processCfgValue(expr)
      case CfgBinOpExpr(_, leftExpr, rightExpr) =>
        _processCfgValue(leftExpr)
        _processCfgValue(rightExpr)
    }

    variables.toSet
  }

//  /**
//   * Assigns variables to virtual registers while attempting to minimize
//   * the number of registers used subject to the constraints.
//   *
//   * @param statements statements to be executed in order
//   * @param constraintBefore map of variables to locations of values of variables at the beginning
//   *                         of the block.
//   * @param constraintAfter map of variables to locations that values of the variables must
//   *                        be in at the end of the block.
//   * @param globalVariables set of global variables
//   * @return Tuple2 consisting of the register allocated statements and the number of
//   *         registers used.
//   */
//  def assignRegisters(statements: List[CfgStatement],
//    constraintBefore: Map[CfgVariable, VirtualLocation],
//    constraintAfter: Map[CfgVariable, VirtualLocation],
//    globalVariables: Set[CfgVariable]
//  ): (List[RegStatement[VirtualLocation]], Int) = {
//    ???
//  }
//
//  def virtualize(scope: CfgScope): VirtualRegisterScope = {
//    ???
//  }
//
//  /**
//   * Splits a scope so that it uses at most maxRegisters.
//   *
//   * @param virtualizedScope scope to be split.
//   * @param maxRegisters register number limit.
//   * @return scope meeting these requirements.
//   */
//  def split(virtualizedScope: VirtualRegisterScope, maxRegisters: Int): SplitScope = {
//    ???
//  }
//
//  /**
//   * Converts a virtual scope into real scope by assigning real registers and stack
//   * offsets to virtual registers and stack offsets.
//   *
//   * @param splitScope split scope
//   * @return a scope with real locations compatible with codegen
//   */
//  def realize(splitScope: SplitScope): RegScope = {
//    ???
//  }
}
