package joinwiz

import org.apache.spark.sql.Column

import scala.annotation.tailrec
import scala.language.experimental.macros
import scala.language.higherKinds
import scala.reflect.macros.blackbox

trait Expr[L, R] {
  def apply(): Column
  def apply(left: L, right: R): Boolean
}

object Expr {
  def expr[L, R](f: (L, R) => Boolean)(c: Column): Expr[L, R] = new Expr[L, R] {
    override def apply(): Column                   = c
    override def apply(left: L, right: R): Boolean = f(left, right)
  }
}

sealed trait TCol[O, +T] {
  def column: Column
  def apply(value: O): T
}
trait LTCol[LO, RO, +T] extends TCol[LO, T]
trait RTCol[LO, RO, +T] extends TCol[RO, T]

trait ExtractTColSyntax {
  implicit class BasicLTColExtract[LO, RO, E](val applyLTCol: ApplyLTCol[LO, RO, E]) {
    def apply[T](expr: E => T): LTCol[LO, RO, T] = macro ApplyCol.leftColumn[LO, RO, E, T]
  }

  implicit class BasicRTColExtract[LO, RO, E](val applyRTCol: ApplyRTCol[LO, RO, E]) {
    def apply[T](expr: E => T): RTCol[LO, RO, T] = macro ApplyCol.rightColumn[LO, RO, E, T]
  }

  implicit class OptionLTColExtract[LO, RO, E](val applyLTCol: ApplyLTCol[LO, RO, Option[E]]) {
    def apply[T](expr: E => T): LTCol[LO, RO, Option[T]] = macro ApplyCol.leftOptColumn[LO, RO, E, T]
  }

  implicit class OptionRTColExtract[LO, RO, E](val applyRTCol: ApplyRTCol[LO, RO, Option[E]]) {
    def apply[T](expr: E => T): RTCol[LO, RO, Option[T]] = macro ApplyCol.rightOptColumn[LO, RO, E, T]
  }
}

class ApplyLTCol[LO, RO, E](val names: Seq[String], val orig: LO => E) extends Serializable {
  private[joinwiz] def map[E1](name: String, newOrig: E => E1) = new ApplyLTCol[LO, RO, E1](names :+ name, newOrig compose orig)
}

class ApplyRTCol[LO, RO, E](val names: Seq[String], val orig: RO => E) extends Serializable {
  private[joinwiz] def map[E1](name: String, newOrig: E => E1) = new ApplyRTCol[LO, RO, E1](names :+ name, newOrig compose orig)
}

object ApplyLTCol {
  def apply[L, R] = new ApplyLTCol[L, R, L](names = Left.alias :: Nil, identity)
}

object ApplyRTCol {
  def apply[L, R] = new ApplyRTCol[L, R, R](names = Right.alias :: Nil, identity)
}

private[joinwiz] object Left {
  val alias = "LEFT"
}
private[joinwiz] object Right {
  val alias = "RIGHT"
}

private object ApplyCol {

  def leftColumn[LO: c.WeakTypeTag, RO: c.WeakTypeTag, E: c.WeakTypeTag, T: c.WeakTypeTag](c: blackbox.Context)(expr: c.Expr[E => T]): c.Expr[LTCol[LO, RO, T]] = {
    import c.universe._

    val leftType  = c.weakTypeOf[LO]
    val rightType = c.weakTypeOf[RO]
    val tType     = c.weakTypeOf[T]
    val name      = extractArgName[E, T](c)(expr)

    c.Expr(
      q"""new joinwiz.LTCol[$leftType, $rightType, $tType] {
            import org.apache.spark.sql.functions.col
            override def apply(value: $leftType): $tType = ($expr compose ${c.prefix}.applyLTCol.orig)(value)
            override def column = col((${c.prefix}.applyLTCol.names :+ $name).mkString("."))
          }"""
    )
  }

  def leftOptColumn[LO: c.WeakTypeTag, RO: c.WeakTypeTag, E: c.WeakTypeTag, T: c.WeakTypeTag](c: blackbox.Context)(expr: c.Expr[E => T]): c.Expr[LTCol[LO, RO, Option[T]]] = {
    import c.universe._

    val leftType  = c.weakTypeOf[LO]
    val rightType = c.weakTypeOf[RO]
    val tType     = c.weakTypeOf[T]
    val name      = extractArgName[E, T](c)(expr)

    c.Expr(
      q"""new joinwiz.LTCol[$leftType, $rightType, Option[$tType]] {
            import org.apache.spark.sql.functions.col
            override def apply(value: $leftType): Option[$tType] = ${c.prefix}.applyLTCol.orig(value).map($expr)
            override def column = col((${c.prefix}.applyLTCol.names :+ $name).mkString("."))
          }"""
    )
  }

  def rightColumn[LO: c.WeakTypeTag, RO: c.WeakTypeTag, E: c.WeakTypeTag, T: c.WeakTypeTag](c: blackbox.Context)(expr: c.Expr[E => T]): c.Expr[RTCol[LO, RO, T]] = {
    import c.universe._

    val leftType  = c.weakTypeOf[LO]
    val rightType = c.weakTypeOf[RO]
    val tType     = c.weakTypeOf[T]
    val name      = extractArgName[E, T](c)(expr)

    c.Expr(
      q"""new joinwiz.RTCol[$leftType, $rightType, $tType] {
            import org.apache.spark.sql.functions.col
            override def apply(value: $rightType): $tType = ($expr compose ${c.prefix}.applyRTCol.orig)(value)
            override def column = col((${c.prefix}.applyRTCol.names :+ $name).mkString("."))
          }"""
    )
  }

  def rightOptColumn[LO: c.WeakTypeTag, RO: c.WeakTypeTag, E: c.WeakTypeTag, T: c.WeakTypeTag](c: blackbox.Context)(expr: c.Expr[E => T]): c.Expr[RTCol[LO, RO, Option[T]]] = {
    import c.universe._

    val leftType  = c.weakTypeOf[LO]
    val rightType = c.weakTypeOf[RO]
    val tType     = c.weakTypeOf[T]
    val name      = extractArgName[E, T](c)(expr)

    c.Expr(
      q"""new joinwiz.RTCol[$leftType, $rightType,  Option[$tType]] {
            import org.apache.spark.sql.functions.col
            override def apply(value: $leftType): Option[$tType] = ${c.prefix}.applyRTCol.orig(value).map($expr)
            override def column = col((${c.prefix}.applyRTCol.names :+ $name).mkString("."))
          }"""
    )
  }

  private def extractArgName[E: c.WeakTypeTag, T: c.WeakTypeTag](c: blackbox.Context)(func: c.Expr[E => T]): String = {
    import c.universe._

    @tailrec
    def extract(tree: c.Tree, acc: List[String]): List[String] = {
      tree match {
        case Ident(_)          => acc
        case Select(q, n)      => extract(q, n.decodedName.toString :: acc)
        case Function(_, body) => extract(body, acc)
        case _                 => c.abort(c.enclosingPosition, s"Unsupported expression: $func, apply should be used for products member selection only")
      }
    }

    extract(func.tree, Nil).mkString(".")
  }
}
