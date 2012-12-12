(ns enlight.core
  (:require [mikera.vectorz.core :as v])
  (:require [enlight.colours :as c])
  (:refer-clojure :exclude [compile])
  (:import [mikera.vectorz Vector3 Vector4 AVector Vectorz])
  (:import [enlight.geom ASceneObject IntersectionInfo])
  (:import [enlight.geom.primitive Sphere SkySphere])
  (:import [java.awt.image BufferedImage])
  (:import [mikera.image]))

(set! *warn-on-reflection* true)
(set! *unchecked-math* true)

(def ^:dynamic *show-warnings* false)

(defmacro error
  "Throws an error with the provided message(s)"
  ([& vals]
    `(throw (enlight.EnlightError. (str ~@vals)))))

(defmacro warn
  "Logs a warning as long as *show-warnings* is bound to true"
  ([& vals]
    `(if *show-warnings*
       (binding [*out* *err*]
         (println (str "WARNING: " ~@vals))))))

;; ===========================================================
;; primitive constructors

(defn sphere 
  (^ASceneObject []
    (sphere (v/vec3) 1.0))
  (^ASceneObject [centre radius]
    (Sphere. (v/vec centre) 1.0)))


;; ===========================================================
;; scene compilation

(defn compile-object 
  "Compiles a scene object"
  ([obj]
    obj))

(defn compile-camera
  "Compiles a camera to ensure necessary vectors are present"
  ([args]
    (let [camera (or args {})
          up (or (:up camera) (v/vec3 0 1 0))
          right (or (:right camera) (v/vec3 1 0 0))
          pos (or (:position camera) (warn "Camera has no position!") (v/vec3))
          dir (or (:direction camera) (warn "Camera has no direction!") (v/vec3 0 0 1))]
      (merge camera
             {:up (v/vec3 up)
              :right (v/vec3 right)
              :direction (v/vec3 dir)
              :position (v/vec3 pos)}))))



(def ENLIGHT-KEYWORDS
  "List of valid enlight keywords in scene description"
  #{:camera :tag :root})

(defn enlight-keyword? [x]
  (ENLIGHT-KEYWORDS x))

(defn compile-element 
  "Compiles a single element of a scene graph with the provided args (may be nil)"
  ([key args]
    (or (enlight-keyword? key) (error "Not a valid enlight keyword! [" key "]"))
    (case key
      :camera (compile-camera args)
      :root (compile-object args)
      :tag args
      (error "Enlight keyword not implemented! [" key "]"))))

(defn update-graph [graph key arg]
  "Updates a graph with a given key and argument. arg may be nil."
  (merge graph {key (compile-element key arg)}))

(defn compile-scene-list
  "Compiles a scene list for rendering into a scene graph"
  ([scene]
    (let [empty-graph {}] 
      (compile-scene-list empty-graph scene)))
  ([graph s]
    (if-let [s (seq s)]
      (compile-scene-list graph (first s) (next s))
      graph))
  ([graph key xs]
    (if-let [s (seq xs)]
      (let [next-item (first s)]
        (if (enlight-keyword? next-item)
          (compile-scene-list (update-graph graph key nil) next-item (next s))
          (compile-scene-list graph key (first s) (next s))))
      (update-graph graph key nil)))
  ([graph key arg xs]
    (compile-scene-list (update-graph graph key arg) xs)))


(defn compile-scene [scene]
  "Compiles a scene for rendering, applying any default behavious and validation"
  (compile-scene-list scene))

(defn position 
  (^Vector3 [camera]
    (or (:position camera) (v/vec3))))

(defn direction 
  (^Vector3 [camera]
    (:direction camera)))

(defn up-direction 
  (^Vector3 [camera]
    (:up camera)))

(defn right-direction 
  (^Vector3 [camera]
    (:right camera)))

;; ======================================================
;; Raytracer core 

(defn trace-ray
  ([^ASceneObject scene-object ^Vector3 pos ^Vector3 dir ^Vector4 colour-result]
    (let [result (IntersectionInfo.)]
      (trace-ray scene-object pos dir colour-result result)))
  ([^ASceneObject scene-object ^Vector3 pos ^Vector3 dir ^Vector4 colour-result ^IntersectionInfo result]
    (.getIntersection scene-object pos dir 0.0 result)
    (if (.hasIntersection result)
      (let [hit-object (.intersectionObject result)
            temp (v/vec3)]
        (.getAmbientColour hit-object pos temp)
        (.copyTo temp colour-result 0))
      (.copyTo dir colour-result 0))))


(defn new-image
  "Creates a new blank image"
  ([w h]
    (mikera.gui.ImageUtils/newImage (int w) (int h))))

(defn render 
  "Render a scene to a new bufferedimage"
  (^BufferedImage [scene
              & {:keys [width height] 
                 :or {width 256 height 256}}]
  (let [width (int width)
        height (int height)
        graph (compile-scene scene)
        ^ASceneObject root (or (:root graph) (error "Scene has no root object!"))
        camera (or (:camera graph) (error "Scene has no camera!"))
        colour-result (v/vec4 [0.5 0 0.8 1])
        ^Vector3 camera-pos (position camera)
        ^Vector3 camera-up (up-direction camera)
        ^Vector3 camera-right (right-direction camera)
        ^Vector3 camera-direction (direction camera)
        ^Vector3 dir (v/vec3)
        ^BufferedImage im (new-image width height)]
    (dotimes [ix width]
      (dotimes [iy height]
        (let [xp (- (/ (double ix) width) 0.5)
              yp (- (/ (double iy) height) 0.5)]
          (.set dir camera-direction)
          (v/add-multiple! dir camera-right xp)
          (v/add-multiple! dir camera-up (- yp))
          (v/normalise! dir)
          (trace-ray (:root graph) camera-pos dir colour-result)
          (.setRGB im ix iy (c/argb-from-vector4 colour-result)))))
    im)))

(defn display
  "Displays an image in a new frame"
  [^BufferedImage image
   & {:keys [title]}]
  (mikera.gui.Frames/displayImage image (str (or title "Enlight Render"))))

(defn show 
  "Renders and displays a scene in a new Frame"
  ([scene
    & {:keys [width height title] 
       :or {width 256 height 256}
       :as params}]
    (display (apply render scene (apply concat params)) :title title)))

(defn testfunction [] 
  (show {})
  (compile-scene {})
)