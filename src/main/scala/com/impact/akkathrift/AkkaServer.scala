package com.impact.akkathrift

import akka.io.{IO=>AIO,_}
import akka.actor._

import org.apache.thrift.server.TServer
import org.apache.thrift.protocol.TProtocol
import org.apache.thrift.transport.TTransport

class AkkaThriftServer(args:AkkaThriftServer.Args) extends TServer(args) {
  override def serve():Unit = {
    args.atss.listenWith(inputProtocolFactory_, processorFactory_)
  }
}

object AkkaThriftServer {
  class Args(val atss:AkkaThriftServerSocket) extends TServer.AbstractServerArgs[Args](atss) {
  }
}
