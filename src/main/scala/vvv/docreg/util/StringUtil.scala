package vvv.docreg.util

import java.util.regex._
import util.matching.Regex

object StringUtil {
  val ValidEmail: Regex = """([a-zA-Z0-9._%+-]+)@([a-zA-Z0-9.-]+\.[a-zA-Z]{2,4})""".r
  val FileName: Regex = """.*\.(.+)""".r

  def nameFromEmail(email: String): String = {
    email indexOf '@' match {
      case -1 => ""
      case i =>
        val namePart = email substring (0, i) replaceAll ("[._%\\-+]"," ") replaceAll ("[0-9]","") replaceAll ("  "," ")
        titleCase(namePart)
    }
  }

  val titleCasePattern = Pattern.compile("(^|\\W)([a-z])")
  def titleCase(s: String): String = {
    val m = titleCasePattern.matcher(s.toLowerCase())
    val sb = new StringBuffer(s.length())
    while (m.find()) {
      m.appendReplacement(sb, m.group(1) + m.group(2).toUpperCase() )
    }
    m.appendTail(sb)
    return sb.toString()
  }

  def prePadTo(input: String, len: Int, pad: Char): String = input.reverse.padTo(len, pad).reverse
    /* var x = key
    List.range(x.size, 4).foreach((i) => x = "0" + x)
    x */

  def fileExtension(fileName: String): Option[String] = fileName match {
    case FileName(extension) => Some(extension)
    case _ => None
  }
}
