package spray.testkit

import org.specs2.execute.{ Failure, FailureException }
import org.specs2.specification.core.{ Fragments, SpecificationStructure }
import org.specs2.specification.create.DefaultFragmentFactory

/**
 * Spray's built-in support for specs2 is built against specs2 2.x, not 3.x.
 * So you cannot use the Specs2Interface from spray but need to compile one yourself (against specs2 3.x).
 * That is what this code does, taken from https://gist.github.com/gmalouf/51a8722b50f6a9d30404
 * Note that the build has to exclude Specs2 as a transitive dependency from the Spray testkit.
 */
trait Specs2Interface extends TestFrameworkInterface with SpecificationStructure {
  def failTest(msg: String) = {
    val trace = new Exception().getStackTrace.toList
    val fixedTrace = trace.drop(trace.indexWhere(_.getClassName.startsWith("org.specs2")) - 1)
    throw new FailureException(Failure(msg, stackTrace = fixedTrace))
  }

  override def map(fs: ⇒ Fragments) = super.map(fs).append(DefaultFragmentFactory.step(cleanUp()))
}

trait NoAutoHtmlLinkFragments extends org.specs2.specification.dsl.ReferenceDsl {
  override def linkFragment(alias: String) = super.linkFragment(alias)

  override def seeFragment(alias: String) = super.seeFragment(alias)
}