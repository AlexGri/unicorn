package org.virtuslab.unicorn.repositories

import java.sql.SQLException
import org.virtuslab.unicorn.{ HasJdbcDriver, Identifiers, Tables }
import scala.Some

protected[unicorn] trait IdRepositories {
  self: HasJdbcDriver with Identifiers with Tables =>

  import driver.simple._

  /**
   * Base class for all queries with an [[org.virtuslab.unicorn.Identifiers.BaseId]].
   *
   * @tparam Id type of id
   * @tparam Entity type of elements that are queried
   * @tparam Table type of table
   */
  protected trait BaseIdQueries[Id <: BaseId, Entity <: WithId[Id], Table <: IdTable[Id, Entity]] {

    /** @return query to operate on */
    protected def query: TableQuery[Table]

    /** @return type mapper for I, required for querying */
    protected implicit def mapping: BaseColumnType[Id]

    val byIdQuery = Compiled(byIdFunc _)

    /** Query all ids. */
    protected lazy val allIdsQuery = query.map(_.id)

    /** Query element by id, method version. */
    protected def byIdFunc(id: Column[Id]) = query.filter(_.id === id)

    /** Query by multiple ids. */
    protected def byIdsQuery(ids: Seq[Id]) = query.filter(_.id inSet ids)
  }

  /**
   * Base trait for repositories where we use [[org.virtuslab.unicorn.Identifiers.BaseId]]s.
   *
   * @tparam Id type of id
   * @tparam Entity type of entity
   * @tparam Table type of table
   */
  // format: OFF
  class BaseIdRepository[Id <: BaseId, Entity <: WithId[Id], Table <: IdTable[Id, Entity]](protected val query: TableQuery[Table])
                                                                                          (implicit val mapping: BaseColumnType[Id])
      extends BaseIdQueries[Id, Entity, Table] {
    // format: ON

    protected def queryReturningId = query returning query.map(_.id)

    final val tableName = query.baseTableRow.tableName

    /**
     * @param session implicit session param for query
     * @return all elements of type A
     */
    final def findAll()(implicit session: Session): Seq[Entity] = query.list

    /**
     * Deletes all elements in table.
     * @param session implicit session param for query
     * @return number of deleted elements
     */
    final def deleteAll()(implicit session: Session): Int = query.delete

    /**
     * Finds one element by id.
     *
     * @param id id of element
     * @param session implicit session
     * @return Option(element)
     */
    final def findById(id: Id)(implicit session: Session): Option[Entity] = byIdQuery(id).firstOption

    /**
     * Clones element by id.
     *
     * @param id id of element to clone
     * @param session implicit session
     * @return Option(id) of new element
     */
    final def copyAndSave(id: Id)(implicit session: Session): Option[Id] = findById(id).map(elem => queryReturningId insert elem)

    /**
     * Finds one element by id.
     *
     * @param id id of element
     * @param session implicit session
     * @return Option(element)
     */
    final def findExistingById(id: Id)(implicit session: Session): Entity =
      findById(id).getOrElse(throw new NoSuchFieldException(s"For id: $id in table: $tableName"))

    /**
     * Finds elements by given ids.
     *
     * @param ids ids of element
     * @param session implicit session
     * @return Seq(element)
     */
    final def findByIds(ids: Seq[Id])(implicit session: Session): Seq[Entity] = byIdsQuery(ids).list

    /**
     * Deletes one element by id.
     *
     * @param id id of element
     * @param session implicit session
     * @return number of deleted elements (0 or 1)
     */
    final def deleteById(id: Id)(implicit session: Session): Int = byIdQuery(id).delete
      .ensuring(_ <= 1, "Delete by id removed more than one row")

    /**
     * @param session implicit session
     * @return Sequence of ids
     */
    final def allIds()(implicit session: Session): Seq[Id] = allIdsQuery.list

    /**
     * Saves one element.
     *
     * @param elem element to save
     * @param session implicit session
     * @return Option(elementId)
     */
    final def save(elem: Entity)(implicit session: Session): Id = {
      elem.id match {
        case Some(id) =>
          val rowsUpdated = byIdFunc(id).update(elem)
          if (rowsUpdated == 1) id
          else throw new SQLException(s"Error during save in table: $tableName, " +
            s"for id: $id - $rowsUpdated rows updated, expected: 1. Entity: $elem")
        case None =>
          queryReturningId insert elem
      }
    }

    /**
     * Saves multiple elements.
     *
     * @param elems elements to save
     * @param session implicit database session
     * @return Sequence of ids
     */
    final def saveAll(elems: Seq[Entity])(implicit session: Session): Seq[Id] = session.withTransaction {
      // conversion is required to force lazy collections
      elems.toIndexedSeq map save
    }

    /**
     * Creates table definition in database.
     *
     * @param session implicit database session
     */
    final def create()(implicit session: Session): Unit =
      query.ddl.create

    /**
     * Drops table definition from database.
     *
     * @param session implicit database session
     */
    final def drop()(implicit session: Session): Unit =
      query.ddl.drop
  }

}
