package vvv.docreg.backend

import net.liftweb.ldap._
import javax.naming.directory._
import net.liftweb.common.{Full, Failure, Empty, Box}

/*
https://wiki.shibboleth.net/confluence/display/SHIB2/IdPADConfigIssues
http://download.oracle.com/javase/jndi/tutorial/ldap/referral/jndi.html
*/

/*
userPrincipalName: sabernethy@GNET.global.vpn    (must be unique)
mailNickname: sabernethy
mail Scott.Abernethy@Aviatnet.com
name SAbernethy
sAMAccountName: sabernethy
sn: Abernethy
givenName: Scott
displayName: Scott Abernethy
distinguishedName: CN=SAbernethy,OU=NZ,OU=People,OU=APAC,DC=GNET,DC=global,DC=vpn
 */

case class UserAttributes(userName: String, mail: String, displayName: String)

class Directory extends LDAPVendor {
  val ldapBase = "DC=GNET,DC=global,DC=vpn"
  configure(
    Map(
      "ldap.url" -> "ldap://dcgnetnz1.gnet.global.vpn:3268",
//      "ldap.userName" -> "gnet\\sabernethy",
//      "ldap.password" -> "***REMOVED***",
      "ldap.userName" -> "gnet\\***REMOVED***",
      "ldap.password" -> "***REMOVED***",
      "ldap.base" -> ldapBase
    )
  )

  def searchIt(filter: String): List[String] = {
    val sc = new SearchControls()
    sc.setSearchScope(SearchControls.SUBTREE_SCOPE)
    // todo restrict search to attributes we care about?
    searchControls.doWith(sc) {
      search(filter)
    }
  }

  def findFromPartialName(partialName: String): Box[UserAttributes] = {
    find("displayName=*" + partialName.replaceAll(" ", "*") + "*")
  }

  def findFromMail(mailUserName: String): Box[UserAttributes] = {
    find("mail=" + mailUserName + "@aviatnet.com")
  }

  def findFromUserName(userName: String): Box[UserAttributes] = {
    find("userPrincipalName=" + userName + "@GNET.global.vpn")
  }
  
  def find(filter: String): Box[UserAttributes] = {
    searchIt("(&(objectCategory=person)(" + filter + "))") match {
      case Nil => Empty
      case dn :: Nil => findFromDn(dn)
      case dn :: more => Failure("Multiple users found " + (dn :: more))
    }
  }

  def findFromDn(dn: String): Box[UserAttributes] = {
    attributesFromDn(dn + "," + ldapBase) match {
      case null => Empty
      case attr => Full(UserAttributes(
        attr.get("userPrincipalName").toString,
        attr.get("mail").toString,
        attr.get("displayName").toString
      ))
    }
  }
}