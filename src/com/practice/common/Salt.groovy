package com.practice.common

import groovy.json.JsonSlurper
import com.practice.common.Log
import com.practice.common.Blueprint
import com.practice.common.BuildArgs
import com.practice.common.Ffbuild

class Salt {
  def context

  Salt(context) {
    this.context = context
  }

  def saltCall(cmd){
    def log_level = Log.level() == "debug" ? 'debug' : 'info'
    this.context.sh("salt-call -l ${log_level} ${cmd}")
  }

  def saltCallWithOutput(cmd){
    def log_level = Log.level() == "debug" ? 'debug' : 'info'
    this.context.sh(returnStdout: true, script: "salt-call -l ${log_level} ${cmd}")
  }
}
