package com.masterthesis.johannes.annotationtool

import android.Manifest
import androidx.appcompat.app.AppCompatActivity
import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.*
import android.widget.*
import android.content.pm.PackageManager
import android.widget.LinearLayout
import android.content.Context.MODE_PRIVATE
import android.content.IntentSender
import com.google.android.material.snackbar.Snackbar
import androidx.core.content.ContextCompat
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import com.google.android.gms.tasks.Task
import java.io.File
import com.davemorrissey.labs.subscaleview.ImageViewState
import android.graphics.PointF
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import com.reddit.indicatorfastscroll.FastScrollItemIndicator
import com.reddit.indicatorfastscroll.FastScrollerThumbView
import com.reddit.indicatorfastscroll.FastScrollerView
import ru.dimorinny.floatingtextbutton.FloatingTextButton
import java.lang.Exception
import java.lang.IllegalStateException


class MainFragment : Fragment(), FlowerListAdapter.ItemClickListener, View.OnTouchListener, View.OnClickListener, SubsamplingScaleImageView.OnImageEventListener, CompoundButton.OnCheckedChangeListener {
    private lateinit var flowerListView: RecyclerView
    private lateinit var polygonSwitch: Switch
    private lateinit var annotationState: AnnotationState
    private lateinit var imageView: MyImageView
    private lateinit var rightButton: FloatingTextButton
    private lateinit var leftButton: FloatingTextButton
    private lateinit var topButton: FloatingTextButton
    private lateinit var bottomButton: FloatingTextButton

    var restoredImageViewState: ImageViewState? = null
    private var currentEditIndex: Int = 0
    lateinit private var undoButton: MenuItem

    private var startTime: Long = 0
    private var startX: Float = 0.toFloat()
    private var startY: Float = 0.toFloat()

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    private lateinit var imagePath: String

   /** FRAGMENT LIFECYCLE FUNCTIONS **/
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(activity!!)
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult?) {
                locationResult ?: return
                for (location in locationResult.locations){
                    imageView.updateLocation(location)
                }
            }
        }

    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        // Inflate the layout for this fragment
        val fragmentView: View = inflater.inflate(R.layout.fragment_main, container, false)

        flowerListView = fragmentView.findViewById(R.id.flower_list_view)
        flowerListView.setLayoutManager(LinearLayoutManager(context!!))
        var dividerItemDecoration = DividerItemDecoration(flowerListView.getContext(),DividerItemDecoration.VERTICAL);
        flowerListView.addItemDecoration(dividerItemDecoration);



        fragmentView.findViewById<LinearLayout>(R.id.annotation_edit_container).visibility = View.INVISIBLE

        fragmentView.findViewById<Button>(R.id.done_button).setOnClickListener(this)
        fragmentView.findViewById<Button>(R.id.cancel_button).setOnClickListener(this)
        fragmentView.findViewById<ImageButton>(R.id.upButton).setOnClickListener(this)
        fragmentView.findViewById<ImageButton>(R.id.downButton).setOnClickListener(this)
        fragmentView.findViewById<ImageButton>(R.id.leftButton).setOnClickListener(this)
        fragmentView.findViewById<ImageButton>(R.id.rightButton).setOnClickListener(this)
        polygonSwitch = fragmentView.findViewById<Switch>(R.id.polygonSwitch)
        polygonSwitch.setOnCheckedChangeListener(this)

        rightButton = fragmentView.findViewById<FloatingTextButton>(R.id.floating_button_right)
        leftButton = fragmentView.findViewById<FloatingTextButton>(R.id.floating_button_left)
        topButton = fragmentView.findViewById<FloatingTextButton>(R.id.floating_button_top)
        bottomButton = fragmentView.findViewById<FloatingTextButton>(R.id.floating_button_bottom)
        rightButton.setOnClickListener(object : View.OnClickListener { override fun onClick(view: View) {loadNextTile(R.id.floating_button_right)} })
        leftButton.setOnClickListener(object : View.OnClickListener { override fun onClick(view: View) {loadNextTile(R.id.floating_button_left)} })
        bottomButton.setOnClickListener(object : View.OnClickListener { override fun onClick(view: View) {loadNextTile(R.id.floating_button_bottom)} })
        topButton.setOnClickListener(object : View.OnClickListener { override fun onClick(view: View) {loadNextTile(R.id.floating_button_top)} })


        return fragmentView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if(savedInstanceState != null && savedInstanceState.containsKey(IMAGE_VIEW_STATE_KEY)) {
            restoredImageViewState = savedInstanceState.getSerializable(IMAGE_VIEW_STATE_KEY) as ImageViewState
        }

        val prefs = context!!.getSharedPreferences(SHARED_PREFERENCES_KEY, MODE_PRIVATE)
        val restoredText = prefs.getString(LAST_OPENED_IMAGE_URI, null)
        if (restoredText != null) {
            imagePath = restoredText
            requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE), READ_PHONE_STORAGE_RETURN_CODE_STARTUP)
        }
    }

    override fun onResume() {
        super.onResume()
        if(::annotationState.isInitialized){
            if (annotationState.hasLocationInformation()){
                stopLocationUpdates()
                startLocationUpdates()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        stopLocationUpdates()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        if(::imageView.isInitialized) {
            val state = imageView.state
            if (state != null) {
                outState.putSerializable(IMAGE_VIEW_STATE_KEY, imageView.state)
            }
            imageView.recycle()
        }
    }

    override fun onDestroyView() {
        if(::imageView.isInitialized) {
            val imageViewContainer: RelativeLayout = view!!.findViewById<RelativeLayout>(R.id.imageViewContainer)
            imageViewContainer.removeView(imageView)
        }
        super.onDestroyView()
    }

    override fun onDestroy() {
        super.onDestroy()
        if(::imageView.isInitialized) {
            imageView.recycle()
        }
    }


    /** OPTIONS MENU FUNCTIONS **/
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.main, menu);
        undoButton = menu.getItem(0)
        enableUndoButton(false)
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        when (item.itemId) {
            R.id.action_import_image ->{
                openImage()
                return false
            }
            R.id.action_undo -> {
                removeCurrentPolygonPoint()
                return false
            }
            else -> return super.onOptionsItemSelected(item)
        }
    }

    fun enableUndoButton(enable: Boolean){
        undoButton.setEnabled(enable)
        if(enable){
            undoButton.getIcon().setAlpha(255);
        }
        else{
            undoButton.getIcon().setAlpha(130);
        }
    }



    /** CONTROL VIEW FUNCTIONS **/


    override fun onItemClick(view: View, position: Int) {
        //TODO
        (flowerListView.adapter as FlowerListAdapter).selectedIndex(position)
        imageView.invalidate()
    }

    override fun onClick(view: View) {
        when(view.id){
            R.id.done_button -> {
                if(annotationState.currentFlower!!.isPolygon && annotationState.currentFlower!!.polygon.size < 3){
                    Snackbar.make(view!!, R.string.to_small_polygon, Snackbar.LENGTH_LONG).show();
                }
                else{
                    annotationState.permanentlyAddCurrentFlower()
                    updateControlView()
                    imageView.invalidate()
                    polygonSwitch.isChecked = false
                }
            }
            R.id.cancel_button -> {
                annotationState.cancelCurrentFlower()
                updateControlView()
                imageView.invalidate()
            }
            R.id.upButton, R.id.downButton, R.id.leftButton, R.id.rightButton -> {
                moveCurrentMark(view.id)
            }
        }
    }

    override fun onCheckedChanged(switch: CompoundButton, checked: Boolean) {
        when(switch.id){
            R.id.polygonSwitch ->{
                if(annotationState.currentFlower != null){
                    annotationState.currentFlower!!.isPolygon = checked
                    imageView.invalidate()
                }
            }
        }
    }

    fun updateControlView(){
        if(annotationState.currentFlower == null){
            view!!.findViewById<LinearLayout>(R.id.annotation_edit_container).visibility = View.INVISIBLE
            flowerListView.adapter = null
            enableUndoButton(false)
        }
        else{
            view!!.findViewById<LinearLayout>(R.id.annotation_edit_container).visibility = View.VISIBLE
            var adapter = FlowerListAdapter(context!!,annotationState);
            adapter.setClickListener(this);
            flowerListView.setAdapter(adapter)
            try {
                var fastScrollerView: FastScrollerView = view!!.findViewById<FastScrollerView>(R.id.fastscroller)
                fastScrollerView.setupWithRecyclerView(
                    flowerListView,
                    { position ->
                        FastScrollItemIndicator.Text(
                            adapter.getPreview(position) // Grab the first letter and capitalize it
                        ) // Return a text indicator
                    },
                    showIndicator = { indicator, indicatorPosition, totalIndicators ->
                        println("height" + fastScrollerView.height)
                        if(fastScrollerView.height<41*totalIndicators){
                            fastScrollerView.textPadding = 0.5F
                            indicatorPosition % 1 == 0
                        }
                        else{
                            indicatorPosition % 1 == 0
                        }
                        // Hide every other indicator
                    }
                )
                var fastScrollerThumbView: FastScrollerThumbView =
                    view!!.findViewById<FastScrollerThumbView>(R.id.fastscroller_thumb)
                fastScrollerThumbView.setupWithFastScroller(fastScrollerView)
            }
            catch (e: IllegalStateException){

            }
        }
    }


    /** IMAGE VIEW FUNCTIONS **/
    fun loadNextTile(id:Int){
        when(id){
            R.id.floating_button_right -> {
                val column: Int = imagePath.substringAfter("col").substringBefore('.').toInt()
                val regex: Regex = "col([0-9]|[0-9][0-9]|[0-9][0-9][0-9]).".toRegex()
                imagePath = regex.replace(imagePath,"col" +(column+1).toString() + ".")
            }
            R.id.floating_button_left -> {
                val column: Int = imagePath.substringAfter("col").substringBefore('.').toInt()
                val regex: Regex = "col([0-9]|[0-9][0-9]|[0-9][0-9][0-9]).".toRegex()
                imagePath = regex.replace(imagePath,"col" +(column-1).toString() + ".")
            }
            R.id.floating_button_top -> {
                val row: Int = imagePath.substringAfter("row").substringBefore('_').toInt()
                val regex: Regex = "row([0-9]|[0-9][0-9]|[0-9][0-9][0-9])_".toRegex()
                imagePath = regex.replace(imagePath,"row" +(row-1).toString() + "_")
            }
            R.id.floating_button_bottom -> {
                val row: Int = imagePath.substringAfter("row").substringBefore('_').toInt()
                val regex: Regex = "row([0-9]|[0-9][0-9]|[0-9][0-9][0-9])_".toRegex()
                imagePath = regex.replace(imagePath,"row" +(row+1).toString() + "_")
            }
        }

        val currScale = imageView.scale
        val old_center = imageView.center
        annotationState.cancelCurrentFlower()
        updateControlView()
        restoredImageViewState = null
        bottomButton.visibility = View.INVISIBLE
        topButton.visibility = View.INVISIBLE
        leftButton.visibility = View.INVISIBLE
        rightButton.visibility = View.INVISIBLE
        initImageView()
        when(id){
            R.id.floating_button_right -> {
                val new_center: PointF = PointF(0F,old_center!!.y)
                imageView.setScaleAndCenter(currScale,new_center)
            }
            R.id.floating_button_left -> {
                val new_center: PointF = PointF(100000F,old_center!!.y)
                imageView.setScaleAndCenter(currScale,new_center)
            }
            R.id.floating_button_top -> {
                val new_center: PointF = PointF(old_center!!.x,100000F)
                imageView.setScaleAndCenter(currScale,new_center)
            }
            R.id.floating_button_bottom -> {
                val new_center: PointF = PointF(old_center!!.x,0F)
                imageView.setScaleAndCenter(currScale,new_center)
            }
        }
    }

    fun initImageView(){


        if(!isExternalStorageWritable() || !File(imagePath).exists()){
            Snackbar.make(view!!, R.string.could_not_load_image, Snackbar.LENGTH_LONG).show();
            val editor = context!!.getSharedPreferences(SHARED_PREFERENCES_KEY, MODE_PRIVATE).edit()
            editor.putString(LAST_OPENED_IMAGE_URI,null)
            editor.apply()
            return
        }
        println(imagePath)
        view!!.findViewById<ProgressBar>(R.id.progress_circular).visibility = View.VISIBLE
        view!!.findViewById<ProgressBar>(R.id.progress_circular).bringToFront()
        val flowerListSize = getFlowerListFromPreferences(context!!).size
        annotationState = AnnotationState(imagePath, getFlowerListFromPreferences(context!!))
        if(flowerListSize< annotationState.flowerList.size){
            Snackbar.make(view!!, R.string.added_flowers_to_list, Snackbar.LENGTH_LONG).show();
        }
        putFlowerListToPreferences(annotationState.flowerList,context!!)
        val imageViewContainer: RelativeLayout = view!!.findViewById<RelativeLayout>(R.id.imageViewContainer)



        if(::imageView.isInitialized){
            imageView.recycle()
            imageViewContainer.removeView(imageView)
        }

        imageView = MyImageView(context!!,annotationState,rightButton,leftButton,topButton,bottomButton, stateToRestore = restoredImageViewState)
        imageView.setOnTouchListener(this)
        imageView.setOnImageEventListener(this)
        imageViewContainer.addView(imageView)

        val editor = context!!.getSharedPreferences(SHARED_PREFERENCES_KEY, MODE_PRIVATE).edit()
        editor.putString(LAST_OPENED_IMAGE_URI,imagePath)
        editor.apply()
        if(annotationState.hasLocationInformation()){
            startLocationUpdates()
        }
    }

    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    private fun startLocationUpdates(){
        if (ContextCompat.checkSelfPermission(activity!!, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), LOCATION_PERMISSION_REQUEST)
        }
        else{
            val locationRequest = LocationRequest.create()?.apply {
                interval = 6000
                fastestInterval = 3000
                priority = LocationRequest.PRIORITY_HIGH_ACCURACY
            }
            val builder = LocationSettingsRequest.Builder()
                .addLocationRequest(locationRequest!!)
            val client: SettingsClient = LocationServices.getSettingsClient(activity!!)
            val task: Task<LocationSettingsResponse> = client.checkLocationSettings(builder.build())
            task.addOnSuccessListener { locationSettingsResponse ->
                fusedLocationClient.requestLocationUpdates(locationRequest,
                    locationCallback,
                    null /* Looper */)
            }

            task.addOnFailureListener { exception ->
                if (exception is ResolvableApiException){
                    try {
                        startIntentSenderForResult(exception.getResolution().getIntentSender(), TURN_ON_LOCATION_USER_REQUEST, null, 0, 0, 0, null);
                    } catch (sendEx: IntentSender.SendIntentException) {
                    }
                }
            }



        }
    }

    private fun moveCurrentMark(id: Int){
        when(id){
            R.id.leftButton -> {
                annotationState.currentFlower!!.decrementXPos(currentEditIndex)
            }
            R.id.rightButton -> {
                annotationState.currentFlower!!.incrementXPos(currentEditIndex)
            }
            R.id.upButton -> {
                annotationState.currentFlower!!.decrementYPos(currentEditIndex)
            }
            R.id.downButton -> {
                annotationState.currentFlower!!.incrementYPos(currentEditIndex)
            }
        }
        imageView.invalidate()
    }

    private fun openImage(){
        requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE), READ_PHONE_STORAGE_RETURN_CODE)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out kotlin.String>, grantResults: IntArray): Unit {
        if(requestCode == READ_PHONE_STORAGE_RETURN_CODE){
            if (permissions[0].equals(Manifest.permission.READ_EXTERNAL_STORAGE) && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "image/*"
                }
                startActivityForResult(intent, OPEN_IMAGE_REQUEST_CODE)
            }
        }
        else if(requestCode == READ_PHONE_STORAGE_RETURN_CODE_STARTUP){
            if (grantResults.isNotEmpty() && permissions[0].equals(Manifest.permission.READ_EXTERNAL_STORAGE) && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initImageView()
            }

        }
        else if(requestCode == LOCATION_PERMISSION_REQUEST) {
            //TODO: arrayoutofbounds exception
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startLocationUpdates()
            }
        }
    }

    override fun onReady() {
        view!!.findViewById<ProgressBar>(R.id.progress_circular).visibility = View.INVISIBLE
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {

        if (requestCode == OPEN_IMAGE_REQUEST_CODE && resultCode == AppCompatActivity.RESULT_OK) {
            resultData?.data?.also { uri ->
                imagePath = uriToPath(uri)
                restoredImageViewState = null
                initImageView()
            }
        }
        if (requestCode == TURN_ON_LOCATION_USER_REQUEST) {
            startLocationUpdates()
        }
    }

    override fun onTouch(view: View, event: MotionEvent): Boolean {

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                startX = event.x
                startY = event.y
                startTime = System.currentTimeMillis()
            }
            MotionEvent.ACTION_UP -> {
                val endX = event.x
                val endY = event.y
                val endTime = System.currentTimeMillis()
                if (isAClick(startX, endX, startY, endY, startTime, endTime, context!!)) {
                    if(imageView.isEditable()){
                        val editFlower = imageView.clickedOnExistingMark(endX,endY);
                        if(editFlower != null){
                            clickedOnExistingMark(editFlower)
                        }
                        else {
                            clickedOnNewPosition(event)
                        }
                    }
                }
            }
        }
        return false
    }

    private fun clickedOnNewPosition(event: MotionEvent){
        if(annotationState.currentFlower != null && annotationState.currentFlower!!.isPolygon){
            var sourcecoord: PointF = imageView.viewToSourceCoord(PointF(event.x, event.y))!!
            annotationState.currentFlower!!.addPolygonPoint(Coord(sourcecoord.x,sourcecoord.y))
            setCurrentEditIndex(annotationState.currentFlower!!.polygon.size-1)
            if(annotationState.currentFlower!!.polygon.size > 1) enableUndoButton(true)
            imageView.invalidate()
        }
        else{
            var sourcecoord: PointF = imageView.viewToSourceCoord(PointF(event.x, event.y))!!
            annotationState.addNewFlowerMarker(sourcecoord.x, sourcecoord.y)
            setCurrentEditIndex(0)
            updateControlView()
            imageView.invalidate()
        }
    }

    private fun clickedOnExistingMark(flower: Pair<Flower,Int>){
        if(annotationState.currentFlower != null && annotationState.currentFlower!!.isPolygon && annotationState.currentFlower!!.polygon.size < 3){
            Snackbar.make(view!!, R.string.to_small_polygon, Snackbar.LENGTH_LONG).show();
            return
        }
        if(flower.first.isPolygon){
            setCurrentEditIndex(flower.second)
            annotationState.startEditingFlower(flower.first)
            setCurrentEditIndex(flower.second)
            if(annotationState.currentFlower!!.polygon.size > 1) enableUndoButton(true)
            updateControlView()
            imageView.invalidate()
        }
        else{
            annotationState.startEditingFlower(flower.first)
            setCurrentEditIndex(0)
            updateControlView()
            imageView.invalidate()
        }
    }

    private fun removeCurrentPolygonPoint(){
        if(annotationState.currentFlower != null){
            annotationState.currentFlower!!.removePolygonPointAt(currentEditIndex)
            setCurrentEditIndex(annotationState.currentFlower!!.polygon.size-1)
            if(annotationState.currentFlower!!.polygon.size > 1) enableUndoButton(true)
            else enableUndoButton(false)
            imageView.invalidate()
        }
    }

    private fun setCurrentEditIndex(index: Int){
        currentEditIndex = index
        imageView.currentEditIndex = index
    }




    /** UNUSED STUBS **/

    override fun onImageLoaded() {}

    override fun onTileLoadError(e: Exception?) {}

    override fun onPreviewReleased() {}

    override fun onImageLoadError(e: Exception?) {}

    override fun onPreviewLoadError(e: Exception?) {}
}
