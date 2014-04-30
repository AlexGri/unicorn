package org.virtuslab.unicorn.ids

import TestUnicornWithPlay._
import TestUnicornWithPlay.simple._
import play.api.mvc.{PathBindable, QueryStringBindable}
import play.api.data.format.Formatter

/**
 * Created by Łukasz Dubiel on 30.04.14.
 */
class PlayCompanionTest extends BaseTest {

  case class UserId(id: Long) extends BaseId

  object UserId extends IdCompanion[UserId]

  case class User(id: Option[UserId],
                  email: String,
                  firstName: String,
                  lastName: String) extends WithId[UserId]

  class Users(tag: Tag) extends IdTable[UserId, User](tag, "USERS") {

    def email = column[String]("EMAIL", O.NotNull)

    def firstName = column[String]("FIRST_NAME", O.NotNull)

    def lastName = column[String]("LAST_NAME", O.NotNull)

    override def * = (id.?, email, firstName, lastName) <> (User.tupled, User.unapply)
  }

  val usersQuery: TableQuery[Users] = TableQuery[Users]

  it should "have implicit query string binder" in {
    implicitly[QueryStringBindable[UserId]] shouldNot be (null)
  }

  it should "have implicit formatter" in {
    implicitly[Formatter[UserId]] shouldNot be (null)
  }
  
  it should "have implciit path bindable" in {
    implicitly[PathBindable[UserId]] shouldNot be (null)
  }
}
