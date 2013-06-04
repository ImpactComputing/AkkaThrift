package com.impact.akkathrift

import collection.immutable.Queue

import akka.actor._
import akka.util.{Timeout,ByteString}
import akka.pattern.ask
import akka.io.Tcp

class AkkaThriftConnection(conn: ActorRef) extends Actor with ActorLogging with AkkaThriftConfig {
  var toInform: Option[ActorRef] = None
  var readQueue = Queue[(ActorRef, ReadFromBuffer)]()
  var readBuffer = ByteString()
  var connected = true
  var receiving = true

  /* Start listening to the connection */
  override def preStart:Unit = {
    conn ! Tcp.Register(self)
  }

  def receive = {
    case _:Tcp.ConnectionClosed => {
      connected = false
      readQueue.map(_._1).distinct.map(_ ! ConnectionClosed)
    }

    case InformCanRead(who) => {
      toInform = Some(who)
      tryInform
    }

    case Tcp.Received(data) => {
      readBuffer ++= data
      sendQueued
      manageReadBuffer
      tryInform
    }

    case rfb @ ReadFromBuffer(_, _) if connected => {
      readQueue = readQueue :+ (sender, rfb)
      sendQueued
    }
    
    case ConnectionIsAlive => sender ! connected

    case CloseConnection =>  {
      conn ! Tcp.Close
      log.debug("Closing client connection")
      context.stop(self)
    }

    // This needs to be changed to an ack, but this gets it off the ground
    case WriteData(data) if connected => conn ! Tcp.Write(data, ack = Tcp.NoAck)

    case _ if !connected => {
      sender ! ConnectionClosed
    }
  }

  // Make sure we store at least as much data as the request
  def maxBufferSize:Int = readQueue.headOption map {
    case (_, ReadFromBuffer(off,amt)) => (off+amt).max(readBufferSize)
  } getOrElse readBufferSize

  // Send out all queued read requests if we can
  def sendQueued = {
    type Q = Queue[(ActorRef, ReadFromBuffer)]
    @annotation.tailrec
    def loop(q:Q, b:ByteString):(Q,ByteString) = q.headOption match {
      case Some((s, ReadFromBuffer(off, amt))) if (off + amt) <= b.length =>
        val end = off + amt
        s ! ReadData(b.slice(off, end))
        loop(q.tail, b.drop(end))

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
