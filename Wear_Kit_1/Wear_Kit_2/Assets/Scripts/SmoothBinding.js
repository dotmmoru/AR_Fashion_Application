// @input SceneObject[] sources
// @input SceneObject[] targets
// @input vec3 posOffset
// @input vec3 rotOffset
// @input float posLerp
// @input float rotLerp
// @input SceneObject bindTweener

var bound = false;

script.api.intensity = 0;

function updateTransforms(){
    for (var i = 0; i < script.targets.length; i++){
        var sourcePos = script.sources[i].getTransform().getWorldPosition();
        var sourceRot = script.sources[i].getTransform().getWorldRotation();
        
        sourcePos = sourcePos.add(script.posOffset);
        sourceRot = quat.fromEulerAngles(script.rotOffset.x, script.rotOffset.y, script.rotOffset.z).multiply(sourceRot);
        
        
        if (bound){
            var targetPos = script.targets[i].getTransform().getWorldPosition();
            var targetRot = script.targets[i].getTransform().getWorldRotation();
            
            var pos = vec3.lerp(targetPos, sourcePos, 1 - script.api.intensity * (1 - script.posLerp));
            var rot = quat.lerp(targetRot, sourceRot, 1 - script.api.intensity * (1 - script.rotLerp));
        }
        else {
            var pos = sourcePos;
            var rot = sourceRot;
            
            bound = true;
        }
        
        script.targets[i].getTransform().setWorldPosition(pos);
        script.targets[i].getTransform().setWorldRotation(rot);
    }
}

var onUpdate = script.createEvent("UpdateEvent");
onUpdate.enabled = false;
onUpdate.bind(updateTransforms);

var onFaceFound = script.createEvent("FaceFoundEvent");
onFaceFound.bind(function(){
    global.tweenManager.startTween(script.bindTweener, "show");
    
    onUpdate.enabled = true;
});

var onFaceLost = script.createEvent("FaceLostEvent");
onFaceLost.bind(function(){
    global.tweenManager.stopTween(script.bindTweener, "show");
    script.api.intensity = 0;
    onUpdate.enabled = false;
});