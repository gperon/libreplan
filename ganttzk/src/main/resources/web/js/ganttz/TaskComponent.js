zk.$package("ganttz");

ganttz.TaskComponent = zk.$extends(zk.Widget, {
    $define :{
        resourcesText    : null,
        labelsText    : null,
        tooltipText : null
    },
    bind_ : function(event){
        this.$supers('bind_', arguments);
        this.domListen_(this.$n(), "onMouseover", '_showToolTip');
        this.domListen_(this.$n(), "onMouseout", '_hideToolTip');
    },
    unbind_ : function(event){
        this.domUnlisten_(this.$n(), "onMouseout", '_hideToolTip');
        this.domUnListen_(this.$n(), "onMouseover", '_showToolTip');
        this.$supers('unbind_', arguments);
    },
    _showToolTip : function(){
        this._tooltipTimeout = setTimeout(jQuery.proxy(function(offset) {
            var element = jq("#tasktooltip" + this.uuid);
            if (element!=null) {
                element.show();
                offset = ganttz.GanttPanel.getInstance().getXMouse()
                        - element.parent().offset().left
                        - jq('.leftpanelcontainer').offsetWidth
                        - this.$class._PERSPECTIVES_WIDTH
                        + jq('.rightpanellayout div').scrollLeft();
                element.css( 'left' , offset +'px' );
            }
        }, this), this.$class._TOOLTIP_DELAY);
    },
    _hideToolTip : function(){
        if (this._tooltipTimeout) {
            clearTimeout(this._tooltipTimeout);
        }
        jq('#tasktooltip' + this.uuid).hide();
    }

},{
    //"Class" methods and properties
    _TOOLTIP_DELAY : 10, // 10 milliseconds
    _PERSPECTIVES_WIDTH : 80
});