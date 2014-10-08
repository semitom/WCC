package models

import com.datastax.driver.core.{ResultSet, BoundStatement, Cluster, Metadata}
import akka.actor.{ActorSystem, Actor, Props}
import scala.concurrent.duration._
import java.util.Date
import java.text._


/**
 * Created by laptop on 28-9-14.
 */

class Data(location: String, temperature: Float, light: Float) {
  val loc: String = location
  val temp: Float = temperature
  val li: Float = light
}

class DataActor(cluster: Cluster) extends Actor {
  val session = cluster.connect()
  //val prepare_createTable = new BoundStatement(session.prepare("CREATE TABLE IF NOT EXISTS wcc.sensordata (loc text, time timestamp, temperature float, li float, primary key(loc, time));"))
  val prepare_addData = new BoundStatement(session.prepare("INSERT INTO wcc.sensordata(loc, time, temperature, li) VALUES (?, dateOf(now()), ?, ?);"))
  val prepare_getData = new BoundStatement(session.prepare("SELECT * FROM wcc.sensordata WHERE loc = ? ORDER BY time DESC LIMIT 1;"))

  def addData(data: Data) {
    prepare_addData.setString(0, data.loc)
    prepare_addData.setFloat(1, data.temp)
    prepare_addData.setFloat(2, data.li)
    //session.execute(prepare_createTable)
    session.executeAsync(prepare_addData)
  }

  def receive: Receive = {
    case data: Data => addData(data)
  }

}

object CassandraManager {
  val cluster = Cluster.builder.addContactPoint("127.0.0.1").build
  val metadata: Metadata = cluster.getMetadata
  System.out.printf("Connected to cluster: %s\n", metadata.getClusterName)

  import scala.collection.JavaConversions._

  for (host <- metadata.getAllHosts) {
    System.out.printf("Datatacenter: %s; Host: %s; Rack: %s\n", host.getDatacenter, host.getAddress, host.getRack)

  }
  val session = cluster.connect()

  val system = ActorSystem("DataSystem")
  val dataActor = system.actorOf(Props(new DataActor(cluster)), name = "dataactor")

  import system.dispatcher

  /* create data object and add it to the database */
  val data: Data = new Data("north", 20, 2)
  val cancellable = system.scheduler.schedule(0 milliseconds, 1000 milliseconds, dataActor, data)

  def get(loc: String, d_type: String): (Float, String) = {
    val results = session.execute("SELECT * FROM wcc.sensordata WHERE loc = '" + loc + "' ORDER BY time DESC LIMIT 1;")
    var result: Float = 0
    var date: Date = new Date()
    var q_type = ""
    val format = new SimpleDateFormat("HH:mm:ss")

    if(d_type == "light intensity"){
      q_type = "li"
    } else {
      q_type = d_type
    }

    for (row <- results) {
      result = row.getFloat(q_type)
      date = row.getDate("time")
    }
    return (result, format.format(date))
  }

  def get_predicted(loc: String, d_type: String): Float = {
    return 6
  }

  def close {
    cluster.close
  }
}