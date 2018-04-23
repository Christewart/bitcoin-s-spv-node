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
trait CRUDActor[T, PrimaryKeyType] extends Actor {

  private val logger = BitcoinSLogger.logger
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
  def create(t: T): Future[T] = createAll(Seq(t)).map(_.head)


  def createAll(ts : Seq[T]) : Future[Seq[T]]

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

  /** Update the corresponding record in the database */
  def update(t: T): Future[Option[T]] = updateAll(Seq(t)).map(_.headOption)


  /** Updates all of the given ts in the database */
  def updateAll(ts: Seq[T]): Future[Seq[T]] = {
    val query = findAll(ts)
    val actions = ts.map(t => query.update(t))
    val affectedRows: Future[Seq[Int]] = database.run(DBIO.sequence(actions))
    val updatedTs = findAll(ts)
    affectedRows.flatMap { _ =>
      database.run(updatedTs.result)
    }
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
  def upsert(t: T): Future[T] = upsertAll(Seq(t)).map(_.head)

  /** Upserts all of the given ts in the database, then returns the upserted values */
  def upsertAll(ts: Seq[T]): Future[Seq[T]] = {
    logger.info("Attempting to upsert num rows: " + ts.size)
    val actions = ts.map(t => table.insertOrUpdate(t))
    val result: Future[Seq[Int]] = database.run(DBIO.sequence(actions))
    val findQueryFuture = result.map(_ => findAll(ts).result)
    findQueryFuture.flatMap(database.run(_))
  }

  /**
    * return all rows that have a certain primary key
    *
    * @param id
    * @return Query object corresponding to the selected rows
    */
  protected def findByPrimaryKey(id: PrimaryKeyType): Query[Table[_], T, Seq] = findByPrimaryKeys(Seq(id))

  /** Finds the rows that correlate to the given primary keys */
  protected def findByPrimaryKeys(ids: Seq[PrimaryKeyType]): Query[Table[_],T,Seq]

  /**
    * return the row that corresponds with this record
    *
    * @param t - the row to find
    * @return query - the sql query to find this record
    */
  protected def find(t: T): Query[Table[_],  T, Seq] = findAll(Seq(t))

  protected def findAll(ts: Seq[T]): Query[Table[_], T, Seq]

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