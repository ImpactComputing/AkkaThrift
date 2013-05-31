package com.impact.akkathrift

import collection.immutable.Queue

import akka.actor._
import akka.util.{Timeout,ByteString}
import akka.pattern.ask
import akka.io.Tcp

class AkkaThriftConnection(conn: ActorRef) extends Actor with ActorLogging with AkkaThriftConfig {
  var readQueue = Queue[(ActorRef, ReadFromBuffer)]()
  var readBuffer = ByteString()
  var connected = true

  /* Start listening to the connection */
  override def preStart:Unit = {
    conn ! Tcp.Register(self)
  }

  def receive = {
    case _:Tcp.ConnectionClosed =>
      connected = false

    case Tcp.Received(data) => 
      readBuffer ++= data
      sendQueued

    case rfb @ ReadFromBuffer(_, _) =>
      readQueue = readQueue :+ (sender, rfb)
      sendQueued
    
    case ConnectionIsAlive =>
      sender ! connected

    case CloseConnection => 
      conn ! Tcp.Close
      context.stop(self)
  }

  // Make sure we store at least as much data as the request
  def maxBufferSize:Int = readQueue.headOption map {
    case (s, ReadFromBuffer(off,amt)) => (off+amt).max(readBufferSize)
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
    
}
