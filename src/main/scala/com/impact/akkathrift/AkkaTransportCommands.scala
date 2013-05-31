package com.impact.akkathrift

import akka.util.ByteString

sealed trait AkkaTransportRequests
case class ConnectionIsAlive extends AkkaTransportRequests
case class CloseConnection extends AkkaTransportRequests
case class ReadFromBuffer(amount:Int, offset:Int) extends AkkaTransportRequests

sealed trait AkkaTransportResponse
case class ReadData(byte: ByteString)
