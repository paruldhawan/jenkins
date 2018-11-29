#!/usr/bin/groovy
import groovy.json.JsonSlurper

@NonCPS
def parseJson(text) {
  return new JsonSlurper().parseText(text)
}

def call(body) {
  echo "Running in declarative pipeline"
  def config = [:]
  body.resolveStrategy = Closure.DELEGATE_FIRST
  body.delegate = config
  body()

  def log_level = "quiet"
  if(DEBUG) {
    log_level = "debug"


  stage('setup') {
    node("env:dev",{
          // create a function to do this. @todo princetyagi
          echo "HII"
    })
  }
}