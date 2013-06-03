package com.impact.akkathrift

import com.typesafe.config.ConfigFactory
import concurrent.duration._

trait AkkaThriftConfig {
  lazy val conf = {
    val c = ConfigFactory.load()
    c.getConfig("AkkaThrift").withFallback(c)
  }

  //TODO: config all of these
  lazy val waitDelay = conf.getInt("thrift.timeout-delay").seconds
  lazy val readBufferSize = conf.getInt("thrift.read-buffer")
}
