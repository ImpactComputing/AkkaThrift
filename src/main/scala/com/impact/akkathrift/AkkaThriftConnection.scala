package com.impact.akkathrift

import collection.immutable.Queue

import akka.actor._
import akka.util.{Timeout,ByteString}
import akka.pattern.ask
import akka.io.Tcp

class AkkaThriftReadConn(conn: ActorRef) extends Actor with ActorLogging with AkkaThriftConfig {
  var toInform: Option[ActorRef] = None
  var readQueue = Queue[(ActorRef, ReadFromBuffer)]()
  var readBuffer = ByteString.empty
  var connected = true
  var receiving = true

  /* Start listening to the connection */
  override def preStart:Unit = {
    conn ! Tcp.Register(self)
  }

  private[this] def isConnected:PartialFunction[Any,Any] = { case m if connected => m }
  private[this] def connCommands:PartialFunction[Any,Unit] = {
    case InformCanRead(who) => {
      toInform = Some(who)
      tryInform
    }

    // The common case
    case ReadFromBuffer(off,amt) if readQueue.isEmpty & off+amt <= readBuffer.length => {
      readBuffer = sendRead(off, amt, readBuffer, sender)
    }

    case rfb @ ReadFromBuffer(_, _) => {
      readQueue = readQueue :+ (sender, rfb)
      sendQueued
    }

    case Tcp.Received(data) => {
      readBuffer ++= data
      sendQueued
      manageReadBuffer
      tryInform
    }
  }

  private[this] def safeCommands:PartialFunction[Any,Unit] = {
    case _:Tcp.ConnectionClosed => {
      connected = false
      readQueue.map(_._1).distinct.foreach(_ ! ConnectionClosed)
      toInform.foreach(_ ! ConnectionClosed)
    }

    case ConnectionIsAlive => sender ! connected

    case Shutdown =>  {
      conn ! Tcp.Close
      log.debug("Closing client connection")
      context.stop(self)
    }

    case _ => {
      sender ! ConnectionClosed
    }
  }

  def receive = (isConnected andThen (connCommands orElse safeCommands)) orElse safeCommands

  // Make sure we store at least as much data as the request
  def maxBufferSize:Int = readQueue.headOption map {
    case (_, ReadFromBuffer(off,amt)) => (off+amt).max(readBufferSize)
  } getOrElse readBufferSize

  @inline def sendRead(off:Int,amt:Int,buffer:ByteString, sender:ActorRef):ByteString = {
    val end = off + amt
    sender ! ReadData(buffer.slice(off, end))
    buffer.drop(end)
  }

  // Send out all queued read requests if we can
  def sendQueued = {
    type Q = Queue[(ActorRef, ReadFromBuffer)]
    @annotation.tailrec
    def loop(q:Q, b:ByteString):(Q,ByteString) = q.headOption match {
      case Some((s, ReadFromBuffer(off, amt))) if (off + amt) <= b.length =>
        loop(q.tail, sendRead(off, amt, b, s))

      case _ => (q, b)
    }

    // Can't do direct from tuple assignment unless declaring, unfortunately
    val (newRead, newBuff) = loop(readQueue, readBuffer)
    readQueue = newRead
    readBuffer = newBuff 
  }

  // Toggles reading on/off depending if the buffer is full
  def manageReadBuffer = {
    val bufferFull = readBuffer.length >= maxBufferSize 
    if(bufferFull & receiving) {
      conn ! Tcp.SuspendReading
      receiving = false
    } else if(!(bufferFull | receiving)) {
      conn ! Tcp.ResumeReading
      receiving = true
    }
  }

  def tryInform = if(toInform.isDefined & readBuffer.length > 0) {
    toInform.get ! CanRead
    toInform = None
  }
    
}

class AkkaThriftWriteConn(conn: ActorRef) extends Actor with ActorLogging with AkkaThriftConfig {
  object WriteSuccessful extends Tcp.Event
  var writeBuffer = ByteString.empty
  var writing = false

  def receive = { 
    case WriteData(data) => {
      writeBuffer ++= data
      if(!writing) {
        writing = true
        self ! WriteSuccessful
      }
    }

    case WriteSuccessful if writeBuffer.nonEmpty => {
      val (data, remaining) = writeBuffer.splitAt(1024)
      write(data)
      writeBuffer = remaining
    }

    case WriteSuccessful => writing = false

    case Flush if writeBuffer.nonEmpty => {
      write(writeBuffer)
      writeBuffer = ByteString.empty
    }

    case Shutdown => {
      conn ! Tcp.Close
      log.debug("Closing client writer connection")
      context.stop(self)
    }

    case _ => {
      sender ! ConnectionClosed
    }
  }

  def write(data:ByteString) = conn ! Tcp.Write(data, ack = WriteSuccessful)

}
