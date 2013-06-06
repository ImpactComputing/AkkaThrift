package com.impact.akkathrift

import akka.actor.ActorRef

import akka.util.ByteString

sealed trait AkkaTransportRequests
case class ConnectionIsAlive extends AkkaTransportRequests
case class CloseConnection extends AkkaTransportRequests
case class ReadFromBuffer(offset:Int, amount:Int) extends AkkaTransportRequests
case class WriteData(data:ByteString) extends AkkaTransportRequests
case class InformCanRead(who:ActorRef) extends AkkaTransportRequests
case class Flush extends AkkaTransportRequests

sealed trait AkkaTransportResponse
case class ReadData(byte: ByteString) extends AkkaTransportResponse
case class ConnectionClosed extends AkkaTransportResponse
case class CanRead extends AkkaTransportResponse
