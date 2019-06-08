# -*- coding: utf-8 -*-
"""
Created on Fri May 17 18:05:50 2019

@author: johan
"""

from utils import constants

input_folder = constants.predictions
output_folder = "C:/Users/johan/Desktop/predictions/tp"


PATH_TO_LABELS = constants.train_dir + "/model_inputs/label_map.pbtxt"


from utils import file_utils
from object_detection.utils import visualization_utils
from PIL import Image
import os
from object_detection.utils import label_map_util


iou_threshold = 0.4


def evaluate():
    
    
            
    flower_names = get_flower_names_from_labelmap(PATH_TO_LABELS)
    
    images = file_utils.get_all_images_in_folder(input_folder)
    
    for image_path in images:
        
        predictions_path = image_path[:-4] + "_predictions.json"
        ground_truth_path = image_path[:-4] + "_ground_truth.json"
        
        predictions = file_utils.read_json_file(predictions_path)
        ground_truths = filter_ground_truth(file_utils.read_json_file(ground_truth_path),flower_names)
        
        if not predictions or not ground_truths:
            continue
        
        
        for gt in ground_truths:
            gt["hits"] = 0
                
        stats = {}
        
        for flower_name in flower_names:
            stats[flower_name] = {"tp": 0, "fp": 0, "fn": 0}
                
        predictions = sorted(predictions, key=lambda k: -k['score']) 

        for prediction in predictions:
            max_val = 0
            max_i = -1
            for gt_i,ground_truth in enumerate(ground_truths):
                if ground_truth["name"] == prediction["name"]:
                    val = iou(prediction["bounding_box"], ground_truth["bounding_box"])
                    if(val>iou_threshold and val > max_val):
                        max_val = val
                        max_i = gt_i
            
            if max_val > 0 and ground_truths[max_i]["hits"] < 1:
                prediction["label"] = "tp"
                stats[prediction["name"]]["tp"] += 1
                ground_truths[max_i]["hits"] +=  1
            else:
                prediction["label"] = "fp"
                stats[prediction["name"]]["fp"] += 1

        for gt in ground_truths:
            if gt["hits"] < 1:
                stats[gt["name"]]["fn"] += 1
                gt["label"] = "fn"
            else:
                gt["label"] = "tp"


                        
        '''
        for ground_truth in ground_truths:
            max_val = 0
            max_i = -1
            for p_i,prediction in enumerate(predictions):
                if ground_truth["name"] == prediction["name"]:
                    val = iou(prediction["bounding_box"], ground_truth["bounding_box"])
                    if(val>iou_threshold and val > max_val):
                        max_val = val
                        max_i = p_i
            
            if max_val > 0:
                stats[ground_truth["name"]]["tp"] += 1
                predictions[max_i]["hits"] +=  1
            else:
                stats[ground_truth["name"]]["fn"] += 1
        
        for prediction in predictions:
            if prediction["hits"] == 0:
                stats[prediction["name"]]["fp"] += 1
            elif prediction["hits"] > 1:
                print("Damn, a prediction hit more than one ground truths")
        '''
        print(stats)
        for flower_name in flower_names:
            print(flower_name + ":")
            stat = stats[flower_name]
            if float(stat["fp"]+stat["tp"]) == 0:
                precision = "-"
            else:
                precision = float(stat["tp"]) / float(stat["fp"]+stat["tp"])
            if float(stat["tp"] + stat["fn"]) == 0:
                recall = "-"
            else:
                recall = float(stat["tp"]) / float(stat["tp"] + stat["fn"])
        
            print("   precision: " + str(precision))
            print("   recall: " + str(recall))
           
        image = Image.open(image_path)

        for prediction in predictions:
            [top,left,bottom,right] = prediction["bounding_box"]
            if prediction["label"] == "fp":
                visualization_utils.draw_bounding_box_on_image(image,top,left,bottom,right,display_str_list=["FP", prediction["name"]],thickness=2, color="red", use_normalized_coordinates=False)          
        for gt in ground_truths:
            if gt["label"] == "fn":
                [top,left,bottom,right] = gt["bounding_box"]
                visualization_utils.draw_bounding_box_on_image(image,top,left,bottom,right,display_str_list=["FN", gt["name"]],thickness=2, color="red", use_normalized_coordinates=False)          
        image_output_path = os.path.join(output_folder, os.path.basename(image_path))
        image.save(image_output_path)

        
        
def iou(a, b, epsilon=1e-5):
    """ Given two boxes `a` and `b` defined as a list of four numbers:
            [x1,y1,x2,y2]
        where:
            x1,y1 represent the upper left corner
            x2,y2 represent the lower right corner
        It returns the Intersect of Union score for these two boxes.

    Args:
        a:          (list of 4 numbers) [x1,y1,x2,y2]
        b:          (list of 4 numbers) [x1,y1,x2,y2]
        epsilon:    (float) Small value to prevent division by zero

    Returns:
        (float) The Intersect of Union score.
    """
    # COORDINATES OF THE INTERSECTION BOX
    x1 = max(a[0], b[0])
    y1 = max(a[1], b[1])
    x2 = min(a[2], b[2])
    y2 = min(a[3], b[3])

    # AREA OF OVERLAP - Area where the boxes intersect
    width = (x2 - x1)
    height = (y2 - y1)
    # handle case where there is NO overlap
    if (width<0) or (height <0):
        return 0.0
    area_overlap = width * height

    # COMBINED AREA
    area_a = (a[2] - a[0]) * (a[3] - a[1])
    area_b = (b[2] - b[0]) * (b[3] - b[1])
    area_combined = area_a + area_b - area_overlap

    # RATIO OF AREA OF OVERLAP OVER COMBINED AREA
    iou = area_overlap / (area_combined+epsilon)
    return iou

def filter_ground_truth(ground_truths, flower_names):
    filtered_ground_truths = []
    for gt in ground_truths:
        if gt["name"] in flower_names:
            filtered_ground_truths.append(gt)
    return filtered_ground_truths

                
def get_flower_names_from_labelmap(labelmap_path):
    flower_names = []
    category_index = label_map_util.create_category_index_from_labelmap(labelmap_path, use_display_name=True)
    for d in category_index:
        flower_names.append(category_index[d]["name"])
    return flower_names
          
evaluate()
#predictions = file_utils.read_json(input_folder)

