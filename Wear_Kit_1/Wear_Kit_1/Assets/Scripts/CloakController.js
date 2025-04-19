// @input SceneObject headBinding
// @input Asset.Material bodyOccluderMat
// @input Asset.Texture neckMask
// @input Asset.Texture bodyMask

// @input float distThreshold

var isFullBody = false;
var isFace = false;

var canSwitch = true;

var delayedSwitchEnable = script.createEvent("DelayedCallbackEvent");
delayedSwitchEnable.bind(function(){
    canSwitch = true;
});

var camPos = new vec3(0, 0, 40);

function initHands(){
    script.bodyOccluderMat.mainPass.bodyMask = script.neckMask;
}


script.bodyOccluderMat.mainPass.bodyMask = script.neckMask;

function updateTransforms(){
    
    var dist = camPos.distance(script.headBinding.getTransform().getWorldPosition());
    
    if (!isFullBody && (dist > script.distThreshold) && canSwitch){
        isFullBody = true;
        
        script.bodyOccluderMat.mainPass.bodyMask = script.bodyMask;
            
        print("FULL BODY");
        
        canSwitch = false;
        delayedSwitchEnable.reset(0.5);
    }
    else {
        if (isFullBody && (dist <= script.distThreshold) && canSwitch){
            isFullBody = false;
        
            script.bodyOccluderMat.mainPass.bodyMask = script.neckMask;
            
            canSwitch = false;
            delayedSwitchEnable.reset(0.5);
        }
    }           
}

var onUpdate = script.createEvent("UpdateEvent");
onUpdate.enabled = false;
onUpdate.bind(updateTransforms);

var onFaceFound = script.createEvent("FaceFoundEvent");
onFaceFound.bind(function(){    
    onUpdate.enabled = true;  
    isFace = true;
});

var onFaceLost = script.createEvent("FaceLostEvent");
onFaceLost.bind(function(){
    isFace = false;
    onUpdate.enabled = false;
});