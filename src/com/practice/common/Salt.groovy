package com.farfetch.common

import groovy.json.JsonSlurper
import com.farfetch.common.Log
import com.farfetch.common.Blueprint
import com.farfetch.common.BuildArgs
import com.farfetch.common.Ffbuild

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
