// @input SceneObject headBinding
// @input SceneObject[] bodySources
// @input SceneObject[] bodyTargets
// @input SceneObject bodyTweener

// @input SceneObject[] handSources
// @input SceneObject[] handTargets
// @input SceneObject handTweener

// @input Asset.Material bodyOccluderMat
// @input Asset.Texture neckMask
// @input Asset.Texture bodyMask

// @input float posLerp
// @input float rotLerp
// @input float distThreshold

var isFullBody = false;
var isFace = false;

script.api.bodyStrength = 0;
script.api.handStrength = 0;

var camPos = new vec3(0, 0, 40);

var LArmInitRot = script.handTargets[0].getTransform().getLocalRotation();
var RArmInitRot = script.handTargets[3].getTransform().getLocalRotation();

function initHands(){
    script.handTargets[0].getTransform().setLocalRotation(quat.fromEulerAngles(1, 0, 0));
    script.handTargets[1].getTransform().setLocalRotation(quat.fromEulerAngles(0, 0, 0));
    script.handTargets[2].getTransform().setLocalRotation(quat.fromEulerAngles(0, 0, 0));
    script.handTargets[3].getTransform().setLocalRotation(quat.fromEulerAngles(1, 0, 0));
    script.handTargets[4].getTransform().setLocalRotation(quat.fromEulerAngles(0, 0, 0));
    script.handTargets[5].getTransform().setLocalRotation(quat.fromEulerAngles(0, 0, 0));
    
    script.bodyOccluderMat.mainPass.bodyMask = script.neckMask;
}

initHands();

function updateTransforms(){
    
    var dist = camPos.distance(script.headBinding.getTransform().getWorldPosition());
    
    if (!isFullBody && (dist > script.distThreshold)){
        global.tweenManager.startTween(script.handTweener, "show");
        isFullBody = true;
        
        script.bodyOccluderMat.mainPass.bodyMask = script.bodyMask;
            
        print("FULL BODY");
    }
    else {
        if (isFullBody && (dist <= script.distThreshold)){
            global.tweenManager.stopTween(script.handTweener, "show");
            isFullBody = false;
            script.api.handStrength = 0;
        
            initHands();
        }
    }
    
    if (isFullBody){
        for (var i = 0; i < script.handTargets.length; i++){
            var handSourcePos = script.handSources[i].getTransform().getWorldPosition();
            var handSourceRot = script.handSources[i].getTransform().getWorldRotation();
            
            var handTargetPos = script.handTargets[i].getTransform().getWorldPosition();
            var handTargetRot = script.handTargets[i].getTransform().getWorldRotation();
            
            var handPos = vec3.lerp(handTargetPos, handSourcePos, 1 - script.api.handStrength * (1 - script.posLerp));
            var handRot = quat.slerp(handTargetRot, handSourceRot, 1 - script.api.handStrength * (1 - script.rotLerp));
            
            script.handTargets[i].getTransform().setWorldPosition(handPos);
            script.handTargets[i].getTransform().setWorldRotation(handRot);
        }    
    }
        
    for (var i = 0; i < script.bodySources.length; i++){
        var sourcePos = script.bodySources[i].getTransform().getWorldPosition();
        var sourceRot = script.bodySources[i].getTransform().getWorldRotation();
            
        var targetPos = script.bodyTargets[i].getTransform().getWorldPosition();
        var targetRot = script.bodyTargets[i].getTransform().getWorldRotation();
            
        var pos = vec3.lerp(targetPos, sourcePos, 1 - script.api.bodyStrength * (1 - script.posLerp));
        var rot = quat.slerp(targetRot, sourceRot, 1 - script.api.bodyStrength * (1 - script.rotLerp));
            
        script.bodyTargets[i].getTransform().setWorldPosition(pos);
        script.bodyTargets[i].getTransform().setWorldRotation(rot);
    }            
    
    //print(script.api.handStrength);
}

var onUpdate = script.createEvent("UpdateEvent");
onUpdate.enabled = false;
onUpdate.bind(updateTransforms);

var onFaceFound = script.createEvent("FaceFoundEvent");
onFaceFound.bind(function(){    
    onUpdate.enabled = true;
    
    global.tweenManager.startTween(script.bodyTweener, "show");    
    isFace = true;
});

var onFaceLost = script.createEvent("FaceLostEvent");
onFaceLost.bind(function(){
    isFace = false;
    onUpdate.enabled = false;
    
    global.tweenManager.stopTween(script.handTweener, "show");
    script.api.handStrength = 0;
    
    global.tweenManager.stopTween(script.bodyTweener, "show");    
    script.api.bodyStrength = 0;

});