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

(ns ^{ :doc "Util functions related to stream/io."
       :author "kenl" }

  comzotohcljc.util.io )

(import '(java.io
  ByteArrayInputStream ByteArrayOutputStream DataInputStream
  FileInputStream FileOutputStream CharArrayWriter OutputStreamWriter
  File InputStream InputStreamReader OutputStream Reader Writer))
(import '(java.util.zip GZIPInputStream GZIPOutputStream))
(import '(com.zotoh.frwk.io XData XStream))
(import '(org.apache.commons.lang3 StringUtils))
(import '(org.apache.commons.codec.binary Base64))
(import '(org.apache.commons.io IOUtils))
(import '(org.xml.sax InputSource))
(import '(java.nio.charset Charset))

(require '[ comzotohcljc.util.core :as CU])


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(def ^:private HEX_CHS [ \0 \1 \2 \3 \4 \5 \6 \7 \8 \9 \A \B \C \D \E \F ])
(def ^:private SZ_10MEG (* 1024 1024 10))

(defn make-tmpfile "Create a temp file in the temp dir."
  ([] (make-tmpfile "" ""))
  ([^String pfx ^String sux]
    (File/createTempFile
      (if (StringUtils/isEmpty pfx) "tmp-" pfx)
      (if (StringUtils/isEmpty sux) ".dat" sux)
      (com.zotoh.frwk.io.IOUtils/workDir)) ))

(defn newly-tmpfile "Create a new temp file, optionally open it for write as stream."
  ([] (newly-tmpfile false))
  ([open]
    (let [ f (make-tmpfile) ]
      (if open [ f (FileOutputStream. f) ] [ f nil ]))) )

(defn streamify "Wrapped these bytes in an input-stream."
  [bits]
  (if (nil? bits)
    nil
    (ByteArrayInputStream. bits)) )

(defn make-baos "Make a byte array output stream."
  []
  (ByteArrayOutputStream. (int 4096)))

(defn hexify-chars "Turn bytes into hex chars."
  [^bytes bits]
  (let [ len (* 2 (if (nil? bits) 0 (alength bits)))
         out (char-array len) ]
    (when-not (<= len 0)
      (loop [  k 0 pos 0 ]
        (if (>= pos len)
          nil
          (let [ n (bit-and (aget bits k) 0xff) ]
            (aset-char out pos (nth HEX_CHS (bit-shift-right n 4))) ;; high 4 bits
            (aset-char out (+ pos 1) (nth HEX_CHS (bit-and n 0xf))) ;; low 4 bits
            (recur (inc k) (+ 2 pos)) ))))
    out))

(defn hexify-string "Turn bytes into hex string."
  [^bytes bits]
  (if (nil? bits) nil (String. (hexify-chars bits))) )

(defmulti open-file "Open this file path." class)

(defn gzip "Gzip these bytes."
  [^bytes bits]
  (if (nil? bits)
    nil
    (let [ baos (make-baos) ]
      (with-open [ g (GZIPOutputStream. baos) ]
                (.write g bits, 0, (alength bits)))
      (.toByteArray baos))))

(defn gunzip "Gunzip these bytes."
  [^bytes bits]
  (if (nil? bits)
    nil
    (IOUtils/toByteArray (GZIPInputStream. (streamify bits))) ))

(defn reset-stream! "Call reset on this input stream."
  [inp]
  (CU/Try!
    (if-not (nil? inp)  (.reset inp)) ))

(defmethod open-file String
  [ ^String fp]
  (if (nil? fp) nil (XStream. (File. fp))))

(defmethod open-file File
  [^File f]
  (if (nil? f) nil (XStream. f)))

(defn from-gzb64 "Unzip content which is base64 encoded + gziped."
  [^String gzb64]
  (if (nil? gzb64) nil (gunzip (Base64/decodeBase64 gzb64))) )

(defn to-gzb64 "Zip content and then base64 encode it."
  [^bytes bits]
  (if (nil? bits) nil (Base64/encodeBase64String (gzip bits))) )

(defn available "Get the available bytes in this stream."
  [^InputStream inp]
  (if (nil? inp) 0 (.available inp)) )

(defn copy-stream "Copy content from this input-stream to a temp file."
  [^InputStream inp]
  (let [ t (newly-tmpfile true) ]
    (with-open [ os (nth t 1) ]
      (IOUtils/copy inp os))
    (nth t 0)))

(defn copy-bytes "Copy x number of bytes from the source input-stream."
  [^InputStream src ^OutputStream out bytesToCopy]
  (when-not (<= bytesToCopy 0)
    (IOUtils/copyLarge src out 0 bytesToCopy)) )

(defn reset-source! "Reset an input source."
  [^InputSource inpsrc]
  (when-not (nil? inpsrc)
    (let [ rdr (.getCharacterStream inpsrc)
           ism (.getByteStream inpsrc) ]
      (CU/Try!
        (when-not (nil? ism) (.reset ism)) )

      (CU/Try!
        (when-not (nil? rdr) (.reset rdr)) ))))

(defn make-xdata "Return a newly created XData."
  ([] (make-xdata false))
  ([usefile] (if usefile (XData. (make-tmpfile)) (XData.)) ))

(defn- swap-bytes [baos]
  (let [ bits (.toByteArray baos)
         t (newly-tmpfile true) ]
    (doto (nth t 1) (.write bits) (.flush))
    (.close baos)
    t))

(defn- swap-read-bytes [inp baos]
  (let [ bits (byte-array 4096)
         t (swap-bytes baos)
         os (nth t 1) ]
    (try
      (loop [ c (.read inp bits) ]
        (if (< c 0)
          (XData. (nth t 0))
          (if (= c 0)
            (recur (.read inp bits))
            (do (.write os bits 0 c)
                (recur (.read inp bits))))))
      (finally
        (.close os)))) )

(defn- slurp-bytes [inp lmt]
  (let [ bits (byte-array 4096)
         baos (make-baos) ]
    (loop [ c (.read inp bits) cnt 0 ]
      (if (< c 0)
        (XData. baos)
        (if (= c 0)
          (recur (.read inp bits) cnt)
          (do ;; some data
            (.write baos bits 0 c)
            (if (> (+ c cnt) lmt)
              (swap-read-bytes inp baos)
              (recur (.read inp bits) (+ c cnt)) )))))) )

(defn- swap-chars [wtr]
  (let [ bits (.toCharArray wtr)
         t (newly-tmpfile true)
         os (OutputStreamWriter. (nth t 1)) ]
    (doto os (.write bits) (.flush))
    (.close wtr)
    [t os]))

(defn- swap-read-chars [inp wtr]
  (let [ v (swap-chars wtr)
         bits (char-array 4096)
         t (nth v 0)
         os (nth v 1) ]
    (try
      (loop [ c (.read inp bits) ]
        (if (< c 0)
          (XData. (nth t 0))
          (if (= c 0)
            (recur (.read inp bits))
            (do (.write os bits 0 c)
                (recur (.read inp bits))))))
      (finally
        (.close os)))) )

(defn- slurp-chars [inp lmt]
  (let [ wtr (CharArrayWriter. (int 10000))
         bits (char-array 4096) ]
    (loop [ c (.read inp bits) cnt 0 ]
      (if (< c 0)
        (XData. wtr)
        (if (= c 0)
          (recur (.read inp bits) cnt)
          (do
            (.write wtr bits 0 c)
            (if (> (+ c cnt) lmt)
              (swap-read-chars inp wtr)
              (recur (.read inp bits) (+ c cnt)))))))) )

(defn read-bytes "Read bytes and return a XData."
  ([^InputStream inp usefile] (slurp-bytes inp (if usefile 1 (com.zotoh.frwk.io.IOUtils/streamLimit))))
  ([^InputStream inp] (slurp-bytes inp (com.zotoh.frwk.io.IOUtils/streamLimit))) )

(defn read-chars "Read chars and return a XData."
  ([^Reader rdr] (slurp-chars rdr (com.zotoh.frwk.io.IOUtils/streamLimit)))
  ([^Reader rdr usefile] (slurp-chars rdr (if usefile 1 (com.zotoh.frwk.io.IOUtils/streamLimit)))))

(defn morph-chars "Convert these bytes to chars."
  ([^bytes bits] (morph-chars bits (Charset/forName "utf-8")) )
  ([^bytes bits ^Charset charSet]
;;    (1 to min(b.length, count)).foreach { (i) =>
;;      val b1 = b(i-1)
;;      ch(i-1) = (if (b1 < 0) { 256 + b1 } else b1 ).asInstanceOf[Char]
;;    }
    (if (nil? bits)
      nil
      (IOUtils/toCharArray (streamify bits) charSet))) )

(defn diff? "Tests if both streams are the same or different at byte level."
  [inp1 inp2]
  (cond
    (and (nil? inp1) (nil? inp2)) false
    (or (nil? inp1) (nil? inp2)) true
    :else (not (IOUtils/contentEquals inp1 inp2))) )


(def ^:private io-eof nil)

