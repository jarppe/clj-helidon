(ns ring.sse.protocols)


(defprotocol SSEListener
  "A protocol for handling SSE responses. The second argument is an object that 
   satisfies the SSEEmitter protocol."
  (on-open [listener sso-sender] "Called when the SSO is opened."))


(defprotocol SSESender
  "A protocol for sending SSE responses."
  (-send [sender message] "Sends a SSO message")
  (-close [sender] "Closes the SSO response"))
