package com.impact.akkathrift

import concurrent.{Await,TimeoutException}
import concurrent.duration._
import concurrent.ExecutionContext.Implicits.global
import util.{Try,Success,Failure}

import org.apache.thrift.transport.TTransport
import org.apache.thrift.transport.TTransportException

import akka.actor._
import akka.util.{Timeout,ByteString}
import akka.pattern.ask

class AkkaTransport(conn: ActorRef) extends TTransport with AkkaThriftConfig {
  implicit val timeout = Timeout(waitDelay)

  private[this] def isAlive:Try[Boolean] = {
    Try(Await.result(conn ? ConnectionIsAlive, waitDelay).asInstanceOf[Boolean]) 
  }

  @throws[TTransportException]("Some error occurred")
  private[this] def throwNewException(ex:Throwable) = {
    throw new TTransportException(ex match {
      case _:TimeoutException => TTransportException.TIMED_OUT
      case _ => TTransportException.UNKNOWN
    })
  }

  def isOpen():Boolean = {
    isAlive match {
      case Success(alive) => alive
      case Failure(ex) => 
        throwNewException(ex)
        false
    }
  }
  
  def open():Unit = {
    isOpen()
  }

  override def flush():Unit = conn ! Flush

  def close():Unit = {
    conn ! CloseConnection
  }

  override def read(buf:Array[Byte], offset: Int, len:Int):Int= {
    Try(Await.result(conn ? ReadFromBuffer(offset, len), waitDelay).asInstanceOf[AkkaTransportResponse]) match {
      case Success(ReadData(data)) => 
        data.asByteBuffer.get(buf)
        data.length

      case Success(_:ConnectionClosed) =>
        throw new TTransportException(TTransportException.UNKNOWN)

      case Success(_) => throw new TTransportException("Bad command")

      case Failure(ex) => 
        throwNewException(ex)
        -1 // Never actually evaluated
    }
  }

  override def write(buf:Array[Byte], offset:Int, len:Int):Unit = {
    conn ! WriteData(ByteString(buf.slice(offset, offset+len)))
  }

  def informOnRead(who:ActorRef) = conn ! InformCanRead(who)

}
