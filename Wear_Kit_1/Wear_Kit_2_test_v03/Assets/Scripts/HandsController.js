// @input Component.ObjectTracking3D bodytrack
// @input SceneObject[] bodyBones
// @input Asset.Material bodyOccluderMat
// @input Asset.Texture neckMask
// @input Asset.Texture bodyMask

var isFullBody = false;

var bodyBoneNames = ["LeftArm", "RightArm"];

var LArmInitRot = script.bodyBones[0].getTransform().getLocalRotation();
var RArmInitRot = script.bodyBones[1].getTransform().getLocalRotation();

function initHands(){
    script.bodyBones[0].getTransform().setLocalRotation(quat.fromEulerAngles(1, 0, 0));
    script.bodyBones[1].getTransform().setLocalRotation(quat.fromEulerAngles(1, 0, 0));
    
    script.bodyOccluderMat.mainPass.bodyMask = script.neckMask;
}

initHands();

function checkTracking() {    
    //print(isFullBody);    

    var rightLegKey = "RightUpLeg";
    var leftLegKey = "LeftUpLeg";    
    
    if (!isFullBody && (script.bodytrack.isAttachmentPointTracking("LeftLeg") ||
        script.bodytrack.isAttachmentPointTracking("RightLeg"))){
        isFullBody = true;
        
        script.bodyBones[0].getTransform().setLocalRotation(LArmInitRot);
        script.bodyBones[1].getTransform().setLocalRotation(RArmInitRot);
        
        for (var i = 0; i < bodyBoneNames.length; i++){
            script.bodytrack.addAttachmentPoint(bodyBoneNames[i], script.bodyBones[i]);
        }
        
        script.bodyOccluderMat.mainPass.bodyMask = script.bodyMask;
    }
    else {
        if (isFullBody && !script.bodytrack.isAttachmentPointTracking("LeftLeg") &&
            !script.bodytrack.isAttachmentPointTracking("RightLeg")){
            isFullBody = false;
            
            for (var i = 0; i < bodyBoneNames.length; i++){
                script.bodytrack.removeAttachmentPoint(script.bodyBones[i]);
            }
            initHands();
        }
    }    
}

var onUpdate = script.createEvent("UpdateEvent");
onUpdate.bind(checkTracking);

