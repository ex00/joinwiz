package joinwiz

import joinwiz.JoinWiz.{LEFT_DS_ALIAS, RIGHT_DS_ALIAS}
import org.apache.spark.sql.Column

class ColumnEvaluator {

  import org.apache.spark.sql.functions._

  private def column(o: TColumn) = o match {
    case LTColumn(name, _) => col(s"$LEFT_DS_ALIAS.$name")
    case RTColumn(name, _) => col(s"$RIGHT_DS_ALIAS.$name")
  }

  private def const(o: Const[_]) = o match {
    case Const(Some(x)) => lit(x)
    case Const(None) => lit(null)
    case Const(x) => lit(x)
  }

  def evaluate(e: Operator): Column = e match {
    case Equality(left: TColumn, right: TColumn) => column(left) === column(right)
    case Equality(left: TColumn, right: Const[_]) => column(left) === const(right)
    case Equality(left: Const[_], right: TColumn) => column(right) === const(left)
    case And(left, right) => evaluate(left) and evaluate(right)
  }
}