package com.masterthesis.johannes.annotationtool

import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import java.io.*
import kotlin.concurrent.thread


class AnnotationState(@Transient var imagePath: String,@Transient var flowerList: MutableList<String>) {

    var annotatedFlowers: ArrayList<Flower> = ArrayList<Flower>()
    var flowerCount: MutableMap<String,Int> = HashMap<String,Int>()
    var favs: ArrayList<String> = ArrayList<String>()
    var geoInfo: GeoInfo? = null
    @Transient var currentFlower: Flower? = null
    @Transient lateinit var annotationFilePath: String

    init{
        annotationFilePath = createAnnotationFilePath(imagePath)
        var annotationFile: File = File(annotationFilePath)
        var geoInfoFile: File = File(createGeoInfoFilePath(imagePath))
        if(!isExternalStorageWritable()){
            throw Exception("External Storage is not Writable")
            //TODO!!!!! handle gracefully
        }

        if(geoInfoFile.exists()){
            val gson = Gson()
            val reader = JsonReader(FileReader(geoInfoFile))
            val myType = object : TypeToken<GeoInfo>() {}.type
            geoInfo = gson.fromJson<GeoInfo>(reader, myType)
        }



        if(annotationFile.exists()){
            val gson = Gson()
            val reader = JsonReader(FileReader(annotationFile))
            val myType = object : TypeToken<AnnotationState>() {}.type
            val loadedState = gson.fromJson<AnnotationState>(reader, myType)
            annotatedFlowers = loadedState.annotatedFlowers
            favs = loadedState.favs
            flowerCount = loadedState.flowerCount
            for(flower in annotatedFlowers){
                if(!flowerList.contains(flower.name)){
                    flowerList.add(flower.name)
                }
            }
            flowerList = sortList(flowerList)
        }
        for(s: String in flowerList){
            if(!flowerCount.containsKey(s)){
                flowerCount[s] = 0
            }
        }
    }

    fun updateFlowerList(new_list:MutableList<String>):MutableList<String>{
        flowerList = new_list
        for(flower in annotatedFlowers){
            if(!flowerList.contains(flower.name)){
                flowerList.add(flower.name)
            }
        }
        flowerList = sortList(flowerList)
        for(s: String in flowerList){
            if(!flowerCount.containsKey(s)){
                flowerCount[s] = 0
            }
        }
        if(currentFlower!= null && flowerList.isNotEmpty()){
            if(!flowerList.contains(currentFlower!!.name)){
                currentFlower!!.name = flowerList[0]
            }
        }
        return flowerList
    }

    private fun saveToFile(){
        thread{
            val gson = Gson()
            val jsonString = gson.toJson(this);
            val fOut = FileOutputStream(File(annotationFilePath))
            val myOutWriter = OutputStreamWriter(fOut)
            myOutWriter.append(jsonString)
            myOutWriter.close()
            fOut.close()
        }
    }

    private fun updateFavourites(){
        var favourites: ArrayList<String> = ArrayList<String>()
        var flowerCountSorted = flowerCount.toList()
            .sortedBy { (key, value) -> -value }
            .toMap()

        for((key,value) in flowerCountSorted){
            if(value >0 && favourites.size < 5){
                favourites.add(key)
            }
        }
        favs = favourites
    }

    public fun isSelected(flowerName: String): Boolean{
        if(flowerName.equals(currentFlower!!.name)){
            return true
        }
        return false
    }

    public fun isSelected(index: Int): Boolean{
        if(flowerList[index].equals(currentFlower!!.name)){
            return true
        }
        return false
    }

    public fun selectFlower(index: Int){
        if(!currentFlower!!.name.equals(flowerList[index])){
            flowerCount[currentFlower!!.name] = flowerCount[currentFlower!!.name]!! - 1
            flowerCount[flowerList[index]] = flowerCount[flowerList[index]]!! + 1
            //updateFavourites()
        }
        currentFlower!!.name = flowerList[index]
    }

    public fun selectFlower(name: String){
        if(!currentFlower!!.name.equals(name)){
            flowerCount[currentFlower!!.name] = flowerCount[currentFlower!!.name]!! - 1
            flowerCount[name] = flowerCount[name]!! + 1
            //updateFavourites()
        }
        currentFlower!!.name = name
    }

    public fun addNewFlowerMarker(x: Float, y: Float){
        if(currentFlower != null){
            permanentlyAddCurrentFlower()
        }

        var label: String = flowerList[0]
        if(annotatedFlowers.size > 0){
            label = annotatedFlowers[annotatedFlowers.size-1].name
        }
        currentFlower = Flower(label, x,y)
        flowerCount[label] = flowerCount[label]!! + 1

    }

    public fun permanentlyAddCurrentFlower(){
        if(!currentFlower!!.isPolygon){
            currentFlower!!.deletePolygon()
        }
        annotatedFlowers.add(currentFlower!!)
        updateFavourites()
        saveToFile()
        currentFlower = null
    }

    public fun cancelCurrentFlower(){
        flowerCount[currentFlower!!.name] = flowerCount[currentFlower!!.name]!! - 1
        currentFlower = null
    }

    public fun getFlowerColor(name: String, context: Context): Paint{
        val index = flowerList.indexOf(name)
        val ta = context.resources.obtainTypedArray(R.array.colors)
        val paint = Paint()
        val filter = PorterDuffColorFilter(ta.getColor(index%ta.length(),0), PorterDuff.Mode.SRC_IN)
        paint.colorFilter = filter
        ta.recycle()
        return paint
    }

    public fun startEditingFlower(flower: Flower){
        if(currentFlower != null){
            if(currentFlower!!.equals(flower)){
                return
            }
            permanentlyAddCurrentFlower()
        }

        if(annotatedFlowers.contains(flower)){
            currentFlower = flower
            annotatedFlowers.remove(flower)
            saveToFile()
        }
        else{
            throw Exception("Wrooong! The flower must be in the ArrayList! Believe me!")
        }
    }

    fun hasLocationInformation(): Boolean{
        if(geoInfo != null){
            return true
        }
        return false
    }

    fun getTopLeftCoordinates():Pair<Double,Double>{
        if(geoInfo != null){
            return Pair(geoInfo!!.ul_lat,geoInfo!!.ul_lon)
        }
        return Pair(0.0,0.0)
    }

    fun getBottomRightCoordinates():Pair<Double,Double>{
        if(geoInfo != null){
            return Pair(geoInfo!!.lr_lat,geoInfo!!.lr_lon)
        }
        return Pair(0.0,0.0)
    }
}