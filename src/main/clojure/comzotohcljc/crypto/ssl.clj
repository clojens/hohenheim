;;
;; COPYRIGHT (C) 2013 CHERIMOIA LLC. ALL RIGHTS RESERVED.
;;
;; THIS IS FREE SOFTWARE; YOU CAN REDISTRIBUTE IT AND/OR
;; MODIFY IT UNDER THE TERMS OF THE APACHE LICENSE
;; VERSION 2.0 (THE "LICENSE").
;;
;; THIS LIBRARY IS DISTRIBUTED IN THE HOPE THAT IT WILL BE USEFUL
;; BUT WITHOUT ANY WARRANTY; WITHOUT EVEN THE IMPLIED WARRANTY OF
;; MERCHANTABILITY OR FITNESS FOR A PARTICULAR PURPOSE.
;;
;; SEE THE LICENSE FOR THE SPECIFIC LANGUAGE GOVERNING PERMISSIONS
;; AND LIMITATIONS UNDER THE LICENSE.
;;
;; You should have received a copy of the Apache License
;; along with this distribution; if not you may obtain a copy of the
;; License at
;; http://www.apache.org/licenses/LICENSE-2.0
;;

(ns ^{ :doc ""
       :author "kenl" }

  comzotohcljc.crypto.ssl )

(import '(javax.net.ssl X509TrustManager TrustManager))
(import '(javax.net.ssl SSLEngine SSLContext))
(import '(java.net URL))

(require '[comzotohcljc.crypto.stores :as CS])
(require '[comzotohcljc.crypto.core :as CY])


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn make-sslContext "Make a server-side SSLContext."
  ([^URL keyUrl ^comzotohcljc.crypto.codec.PasswordAPI pwdObj]
   (make-sslContext keyUrl pwdObj "TLS"))
  ([^URL keyUrl ^comzotohcljc.crypto.codec.PasswordAPI pwdObj flavor]
    (let [ ctx (SSLContext/getInstance flavor)
           ks (with-open [ inp (.openStream keyUrl) ]
                (if (CY/pkcs-file? keyUrl)
                    (CY/get-pkcsStore inp pwdObj)
                    (CY/get-jksStore inp pwdObj)))
      cs (CS/make-crypto-store ks pwdObj) ]
      (.init ctx
        (-> cs (.keyManagerFactory) (.getKeyManagers))
        (-> cs (.trustManagerFactory) (.getTrustManagers))
        (CY/get-srand))
      ctx)) )


(defn make-sslClientCtx "Make a client-side SSLContext."
  [ssl]
  (if (not ssl)
    nil
    (let [ ctx (SSLContext/getInstance "TLS") ]
      (.init ctx nil (into-array TrustManager [(CY/make-simpleTrustMgr)]) nil)
      ctx)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(def ^:private ssl-eof nil)

