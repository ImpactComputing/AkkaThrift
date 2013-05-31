package com.impact.akkathrift

import akka.util.ByteString

sealed trait AkkaTransportRequests
case class ConnectionIsAlive extends AkkaTransportRequests
case class CloseConnection extends AkkaTransportRequests
case class ReadFromBuffer(offset:Int, amount:Int) extends AkkaTransportRequests
case class WriteData(data:ByteString) extends AkkaTransportRequests

sealed trait AkkaTransportResponse
case class ReadData(byte: ByteString)
