package decaf.compile.reg_alloc.types

import decaf.compile.cfg._

/**
 * Represents a statement with register/ stack allocation information.
 */
abstract sealed class RegStatement[Location <: AsmLocation]()

/**
 * Represents a movement of a value from some location to another, usually
 * because of a splitting/ spoiling.
 *
 * @param from location to copy from.
 * @param to location to write to.
 * @tparam Location see `AsmLocation`
 */
case class MoveStatement[Location <: AsmLocation](
  from: Location,
  to: Location
) extends RegStatement[Location]()

/**
 * Represents a CfgStatement coupled to register/ stack allocation information.
 *
 * @param locationMap map of variables used in the statement to register/ stack location.
 *                    Does not include global variables and arrays.
 * @param origStatement original CfgStatement.
 * @tparam Location see `AsmLocation`
 */
sealed class WithCfgStatement[Location <: AsmLocation](
  val locationMap: Map[CfgVariable, Location],
  val origStatement: CfgStatement
) extends RegStatement[Location]()

/**
 * Represents an assign statement with register/ stack allocation information.
 *
 * @param locationMap @inheritdoc
 * @param origStatement @inheritdoc
 * @tparam Location see `AsmLocation`
 */
case class RegAssignStatement[Location <: AsmLocation](
  override val locationMap: Map[CfgVariable, Location],
  override val origStatement: CfgRegAssignStatement
) extends WithCfgStatement[Location](locationMap, origStatement)

case class RegArrayAssignStatement[Location <: AsmLocation](
  override val origStatement: CfgArrayAssignStatement
) extends WithCfgStatement[Location](locationMap = Map.empty, origStatement)

case class RegFunctionCallStatement[Location <: AsmLocation](
  override val locationMap: Map[CfgVariable, Location],
  override val origStatement: CfgFunctionCallStatement
) extends WithCfgStatement[Location](locationMap, origStatement)

case class RegReturnStatement[Location <: AsmLocation](
  override val locationMap: Map[CfgVariable, Location],
  override val origStatement: CfgReturnStatement
) extends WithCfgStatement[Location](locationMap, origStatement)