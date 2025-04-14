// @input SceneObject headBinding
// @input Asset.Material occluderMat

// @input float distThreshold

// @input Component.Text logger

var isFullBody = false;

var camPos = new vec3(0, 0, 40);

function updateTransforms(){    
    var dist = camPos.distance(script.headBinding.getTransform().getWorldPosition());    
    isFullBody = dist > script.distThreshold;
    
    script.occluderMat.mainPass.useMask = !isFullBody;    
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
    onUpdate.enabled = false;
});