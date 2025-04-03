// @input SceneObject headBinding
// @input Component.ObjectTracking3D bodyTrack
// @input Component.ObjectTracking3D upperTrack
// @input Component.RenderMeshVisual bodymesh
// @input Component.RenderMeshVisual uppermesh

// @input SceneObject[] bodySources
// @input SceneObject[] upperSources
// @input SceneObject[] targets

// @input SceneObject bodyTweener
// @input SceneObject upperTweener

// @input float posLerp
// @input float rotLerp
// @input float distThreshold

// @input Component.Text logger

var upperBound = false;
var bodyBound = false;
var isFullBody = false;

script.api.bodyStrength = 0;
script.api.upperStrength = 0;

var bonesNum = 9;

var camPos = new vec3(0, 0, 40);

function updateTransforms(){
    
    var dist = camPos.distance(script.headBinding.getTransform().getWorldPosition());
    
    //isFullBody = dist > script.distThreshold;
    
    isFullBody = true;
    
    script.bodyTrack.enabled = isFullBody;
    script.bodymesh.enabled = isFullBody;
    
    script.upperTrack.enabled = !isFullBody;
    script.uppermesh.enabled = !isFullBody;   
    
    //script.logger.text = dist.toString(); 
    
    if (isFullBody){
        if (!bodyBound){
            global.tweenManager.stopTween(script.upperTweener, "show");
            global.tweenManager.startTween(script.bodyTweener, "show");
            bodyBound = true;
            upperBound = false;
            script.api.upperStrength = 0;
            
            print("FULL BODY");
        }
        
        for (var i = 0; i < 9; i++){
            var sourcePos = script.bodySources[i].getTransform().getWorldPosition();
            var sourceRot = script.bodySources[i].getTransform().getWorldRotation();
            
            var targetPos = script.targets[i].getTransform().getWorldPosition();
            var targetRot = script.targets[i].getTransform().getWorldRotation();
            
            var pos = vec3.lerp(targetPos, sourcePos, 1 - script.api.bodyStrength * (1 - script.posLerp));
            var rot = quat.lerp(targetRot, sourceRot, 1 - script.api.bodyStrength * (1 - script.rotLerp));
            
            script.targets[i].getTransform().setWorldPosition(pos);
            script.targets[i].getTransform().setWorldRotation(rot);
        }
    }
    else {
        if (!upperBound){
            global.tweenManager.stopTween(script.bodyTweener, "show");
            global.tweenManager.startTween(script.upperTweener, "show");
            upperBound = true;
            bodyBound = false;
            script.api.bodyStrength = 0;
            
            print("UPPER BODY");
        }
        
        for (var i = 0; i < 9; i++){
            var sourcePos = script.upperSources[i].getTransform().getWorldPosition();
            var sourceRot = script.upperSources[i].getTransform().getWorldRotation();
            
            var targetPos = script.targets[i].getTransform().getWorldPosition();
            var targetRot = script.targets[i].getTransform().getWorldRotation();
            
            var pos = vec3.lerp(targetPos, sourcePos, 1 - script.api.upperStrength * (1 - script.posLerp));
            var rot = quat.lerp(targetRot, sourceRot, 1 - script.api.upperStrength * (1 - script.rotLerp));
            
            script.targets[i].getTransform().setWorldPosition(pos);
            script.targets[i].getTransform().setWorldRotation(rot);
        }
        
        //print(1 - script.api.upperStrength * (1 - script.posLerp))
    }
    
    //print(script.api.bodyStrength);
}

var onUpdate = script.createEvent("UpdateEvent");
onUpdate.enabled = false;
onUpdate.bind(updateTransforms);

var onFaceFound = script.createEvent("FaceFoundEvent");
onFaceFound.bind(function(){    
    onUpdate.enabled = true;
});

var onFaceLost = script.createEvent("FaceLostEvent");
onFaceLost.bind(function(){
    
    //onUpdate.enabled = false;
    
    global.tweenManager.stopTween(script.bodyTweener, "show");
    global.tweenManager.stopTween(script.upperTweener, "show");
    
    script.api.bodyStrength = 0;
    script.api.upperStrength = 0;

});