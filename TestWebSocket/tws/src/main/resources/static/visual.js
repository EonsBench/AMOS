var stompClient = null;
var myChart;

$(document).ready(function() {
    connect();
    // 모달을 엽니다
    $('.settings-icon').on('click', function(){
        $('#settingsModal').show();
    });

    // 모달을 닫습니다
    $('.close').on('click', function(){
        $('#settingsModal').hide();
    });

    // 모달 외부를 클릭하면 모달을 닫습니다
    $(window).on('click', function(event){
        if ($(event.target).is('#settingsModal')) {
            $('#settingsModal').hide();
        }
    });

    // 색상 변경 기능
    $('#header-color').on('input', function(){
        var color = $(this).val();
        $('.header').css('background-color', color);
        localStorage.setItem('headerColor', color);
    });

    $('#sidebar-color').on('input', function(){
        var color = $(this).val();
        $('.sidebar').css('background-color', color);
        localStorage.setItem('sidebarColor', color);
    });   
    $('#value-color').on('input', function(){
        var color = $(this).val();
        $('.value-container').each(function() {
            $(this).css('color', color);
        });
        localStorage.setItem('valueColor', color);
    })     
    $('#box-color').on('input', function(){
        var color = $(this).val();
        $('.item').each(function(){
            $(this).css('background-color', color);
        });
        localStorage.setItem('boxColor',color);
    })

    // Select the SVG element after the DOM is ready
    var svg = d3.select("#wind-direction-svg");
    // Initialize temperature bar graph
    var svgTemp = d3.select("#temperature-svg");


    svgTemp.append("rect")
        .attr("x", 75)
        .attr("y", 50)
        .attr("width", 50)
        .attr("height", 300)
        .attr("fill", "#ddd")
        .attr("id", "tempBackground");

    svgTemp.append("rect")
        .attr("x", 75)
        .attr("y", 200)
        .attr("width", 50)
        .attr("height", 0)
        .attr("fill", "green")
        .attr("id", "tempBar");

    // Draw the middle line (0°C)
    svgTemp.append("line")
        .attr("x1", 50)
        .attr("y1", 230)
        .attr("x2", 125)
        .attr("y2", 230)
        .attr("stroke", "black")
        .attr("stroke-width", 2);
    // Draw ticks and labels
    var maxTemp = 60;
    var minTemp = -40;
    var scale = 300 / (maxTemp - minTemp);
    for (var t = minTemp; t <= maxTemp; t++) {
        var y = 350 - (t + 40) * scale;
        var tickLength = t % 5 === 0 ? 10 : 5;
        svgTemp.append("line")
            .attr("x1", 70)
            .attr("y1", y)
            .attr("x2", 70 - tickLength)
            .attr("y2", y)
            .attr("stroke", "black")
            .attr("stroke-width", 1);

        if (t % 5 === 0) {
            svgTemp.append("text")
                .attr("x", 65 - tickLength)
                .attr("y", y)
                .attr("text-anchor", "end")
                .attr("alignment-baseline", "middle")
                .style("fill", "black")
                .style("font-size", "12px")
                .text(t);
        }
    }           
    // SVG 원 그리기
    var circle = svg.append("circle")
        .attr("cx", 200)
        .attr("cy", 200)
        .attr("r", 150)
        .style("fill", "none")
        .style("stroke", "#ccc")
        .style("stroke-width", "2px");
    // Draw ticks around the circle
    for (var angle = 0; angle < 360; angle++) {
        var radians = angle * Math.PI / 180;
        var tickLength = angle % 10 === 0 ? 10 : (angle%5===0?5:0); // Longer ticks every 5 degrees
        var x1 = 200 + 150 * Math.cos(radians);
        var y1 = 200 + 150 * Math.sin(radians);
        var x2 = 200 + (150 - tickLength) * Math.cos(radians);
        var y2 = 200 + (150 - tickLength) * Math.sin(radians);
        svg.append("line")
            .attr("x1", x1)
            .attr("y1", y1)
            .attr("x2", x2)
            .attr("y2", y2)
            .attr("stroke", "black")
            .attr("stroke-width", 2);
    }
    // 8방위 텍스트 레이블 추가
    var directions = ["N", "NE", "E", "SE", "S", "SW", "W", "NW"];
    var angleStep = 360 / directions.length;
    for (var i = 0; i < directions.length; i++) {
        var angle = (270+ i * angleStep)%360;
        var radians = angle * (Math.PI / 180);
        var x = 200 + Math.cos(radians) * 170;
        var y = 200 + Math.sin(radians) * 170;
        svg.append("text")
            .attr("x", x)
            .attr("y", y)
            .attr("text-anchor", "middle")
            .attr("alignment-baseline", "middle")
            .style("font-size", "14px")
            .style("font-weight", "bold")
            .style("fill", "black")
            .text(directions[i]);
    }

    // 화살표 추가
    var arrow = svg.append("path")
        .attr("d", "M 0 -50 L 10 0 L -10 0 Z") // Adjust the path to be centered at (0, 0)
        .style("fill", "blue");
    var windSpeedText = svg.append("text")
        .attr("id", "windSpeedText")
        .attr("x", 200)
        .attr("y", 200)
        .attr("text-anchor", "middle")
        .attr("alignment-baseline", "middle")
        .style("font-size", "24px")
        .style("font-weight", "bold")
        .style("fill", "black")
        .text("0 m/s");
});

function connect() {
    var socket = new SockJS('/websocket');
    stompClient = Stomp.over(socket);
    stompClient.connect({}, function(frame) {
        console.log('Connected: ' + frame);
        stompClient.subscribe('/topic/mqtt', function(message) {
            var obj = JSON.parse(message.body);
            //showMessage(message.body);
            showMessage(obj);
        });
    });
}

function showMessage(obj) {
    $("#message").text(obj.temp);
    $("#humData").text(obj.hum);
    $("#windDirect").text(obj.winddirect);
    $("#windSpeed").text(obj.windspeed);
    $("#minWindSpeed").text(obj.min);
    $("#maxWindSpeed").text(obj.max);
    $("#avgWindSpeed").text(obj.avg);
    var FwindDegree = parseFloat(obj.winddirect);
    var FwindSpeed = parseFloat(obj.windspeed);
    rotateArrow(FwindDegree);
    updateWindSpeed(FwindSpeed,"m/s","16px");
    var temp = parseFloat(obj.temp);
    updateTempBar(temp);
}
// 화살표 회전 함수
function rotateArrow(degrees) {
    // 풍향 값에 따라 각도를 계산합니다. (시계방향으로 90도 회전한 값)
    var angle = (90 - degrees) * Math.PI / 180;

    // 화살표의 위치 계산을 위해 원의 중심 좌표와 반지름을 설정합니다.
    var centerX = 200; // X-coordinate of the circle's center
    var centerY = 200; // Y-coordinate of the circle's center
    var radius = 100; // Radius of the circle

    // 화살표의 끝점 좌표를 계산합니다.
    var x1 = centerX + radius * Math.cos(angle);
    var y1 = centerY - radius * Math.sin(angle); // Y축 방향이 반대로 되어 있으므로 "-" 사용

    // 화살표의 위치를 설정합니다.
    var arrow = d3.select("#wind-direction-svg path");
    arrow.attr("transform", "translate(" + x1 + "," + y1 + ") rotate(" + degrees + ")");
}
function updateWindSpeed(speed,unit,fontsize) {
    d3.select("#windSpeedText").text(speed).append("tspan").text("("+unit+")").style("font-size",fontsize).attr("dy","2em").attr("dx","-3em");
}
function updateTempBar(temp) {
    var maxTemp = 60;
    var minTemp = -40;
    var barHeight = 300; // Height of the temperature bar container
    var scale = barHeight / (maxTemp - minTemp);
    var height = Math.abs(temp) * scale; // Scale temperature to bar height
    var y = 350-(temp+Math.abs(minTemp)) * scale;

    var color;
    if (temp < 0) {
        color = "blue";
    }else {
        color = "red";
    }

    d3.select("#tempBar")
        .transition()
        .duration(500)
        .attr("y", y)
        .attr("height", height)
        .attr("fill", color);
}
