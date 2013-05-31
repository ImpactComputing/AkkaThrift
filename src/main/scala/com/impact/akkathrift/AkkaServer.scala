package com.impact.akkathrift

import akka.io.{IO=>AIO,_}
import akka.actor._

import org.apache.thrift.server.TServer
import org.apache.thrift.protocol.TProtocol
import org.apache.thrift.transport.TTransport

class AkkaThriftServer(args:TServer.Args) extends TServer {
  override def serve():Unit = {
    
  }
}
