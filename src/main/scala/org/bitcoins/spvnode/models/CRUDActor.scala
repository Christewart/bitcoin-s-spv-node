package org.bitcoins.spvnode.models

import akka.actor.Actor
import org.bitcoins.core.util.BitcoinSLogger
import org.bitcoins.spvnode.constant.DbConfig
import slick.backend.DatabaseConfig
import slick.driver.PostgresDriver
import slick.driver.PostgresDriver.api._
import slick.jdbc.DataSourceJdbcDataSource

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success}
/**
  * Created by chris on 9/8/16.
  * This is an abstract actor that can be used to implement any sort of
  * actor that accesses a Postgres database. It creates
  * read, update, upsert, and delete methods for your actor to call.
  * You are responsible for the create function. You also need to specify
  * the table and the database you are connecting to.
  */
trait CRUDActor[T, PrimaryKeyType] extends Actor with BitcoinSLogger {
  /** The table inside our database we are inserting into */
  val table: TableQuery[_ <: Table[T]]

  /** The [[DbConfig]] we used to setup our database connection */
  def dbConfig : DbConfig

  /** Binding to the actual database itself, this is what is used to run querys */
  val database: Database = dbConfig.database

  /**
    * create a record in the database
    *
    * @param t - the record to be inserted
    * @return the inserted record
    */
  def create(t: T): Future[T]

  /**
    * read a record from the database
    *
    * @param id - the id of the record to be read
    * @return Option[T] - the record if found, else none
    */
  def read(id: PrimaryKeyType): Future[Option[T]] = {
    logger.debug("Reading record with id: " + id)
    val query = findByPrimaryKey(id)
    val rows : Future[Seq[T]] = database.run(query.result)
    rows.map(_.headOption)
  }

  /**
    * update the corresponding record in the database
    *
    * @param t - the record to be updated
    * @return int - the number of rows affected by the updated
    */
  def update(t: T): Future[Option[T]] = {
    logger.debug("Updating record " + t )
    val query: Query[Table[_], T, Seq] = find(t)
    database.run(query.update(t)).flatMap { int: Int =>
      val findQuery = find(t)
      val result = database.run(findQuery.result)
      result.map(_.headOption)
    }
  }

  /** Updates all of the given T in the database
    * Only returns records that were updated, i.e.
    * if a row was not found in the database, thus it
    * was not updated, it will not be returned */
  def updateAll(t: Seq[T]): Future[Seq[T]] = {
    val updated: Seq[Future[Option[T]]] = for {
      row <- t
      updated = update(row)
    } yield updated
    Future.sequence(updated).map(_.flatten)
  }

  /**
    * delete the corresponding record in the database
    *
    * @param t - the record to be deleted
    * @return int - the number of rows affected by the deletion
    */
  def delete(t: T): Future[Int] = {
    logger.debug("Deleting record: " + t )
    val query: Query[Table[_], T, Seq] = find(t)
    database.run(query.delete)
  }

  /**
    * insert the record if it does not exist, update it if it does
    *
    * @param t - the record to inserted / updated
    * @return t - the record that has been inserted / updated
    */
  def upsert(t: T): Future[T] = {
    //TODO: Possible bug here with race conditions
    database.run(table.insertOrUpdate(t))
    database.run(find(t).result).map(_.head)
  }

  /**
    * return all rows that have a certain primary key
    *
    * @param id
    * @return Query object corresponding to the selected rows
    */
  protected def findByPrimaryKey(id: PrimaryKeyType): Query[Table[_], T, Seq]

  /**
    * return the row that corresponds with this record
    *
    * @param t - the row to find
    * @return query - the sql query to find this record
    */

  protected def find(t: T): Query[Table[_],  T, Seq]


  override def postStop = database.close()

  /** Sends a message to our parent actor */
  def sendToParent(returnMsg: Future[Any]): Unit = returnMsg.onComplete {
    case Success(msg) =>
      context.parent ! msg
    //context.stop(self)
    case Failure(exception) =>
      //means the future did not complete successfully, we encountered an error somewhere
      logger.error("Exception: " + exception.toString)
      throw exception
  }(context.dispatcher)

}