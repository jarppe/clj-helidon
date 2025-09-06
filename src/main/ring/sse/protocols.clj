(ns ring.sse.protocols)


(defprotocol SSEListener
  "A protocol for handling SSE responses. The second argument is an object that 
   satisfies the SSEEmitter protocol."
  (on-open [listener sso-emitter] "Called when the SSO is opened."))


(defprotocol SSEEmitter
  "A protocol for sending SSE responses."
  (-send [emitter message] "Sends a SSO message")
  (-close [emitter] "Closes the SSO response"))
