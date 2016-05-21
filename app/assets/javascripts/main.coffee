StartController = ($scope, $http, $timeout) ->



  $scope.init = () ->
    console.log("Initializing")
    $scope.obers = []
    $scope.dranken = []
    $scope.alcoholisch = []
    $scope.keuken = []
    $scope.frisdrank = []
    $scope.bestelling = {}
    $scope.bestelling.verkoper = ""
    $scope.totaal = 0
    $scope.error = ''
    $scope.success = ''
    $http.get("/obers/all")
    .success (data, status, headers, config) ->
      console.log(data)
      for o in data
        $scope.obers.push(o)
    .error (data, status, headers, config) ->
      console.error("Obers konden niet worden geladen!")

    $http.get("/dranken/all")
        .success (data, status, headers, config) ->
          console.log(data)
          for o in data
            o.aantal = 0
            if o.categorie == "Alcoholisch"
              $scope.alcoholisch.push(o)
            if o.categorie == "Keuken"
              $scope.keuken.push(o)
            if o.categorie == "Frisdrank"
              $scope.frisdrank.push(o)
            $scope.dranken.push(o)
        .error (data, status, headers, config) ->
          console.error("Dranken konden niet worden geladen!")

  $scope.select = (ober) ->
   if($scope.selectedComponent == ober)
     $scope.selectedOber = undefined
   else
     $scope.selectedOber = ober

  $scope.increase = (drank) ->
    if drank.aantal == '' or 0 or undefined
      drank.aantal = 1
    else
      drank.aantal = parseInt(drank.aantal, 10) + 1
    $scope.calculate()

  $scope.decrease = (drank) ->
    if drank.aantal > 0
      drank.aantal = parseInt(drank.aantal, 10) - 1
    $scope.calculate()

  $scope.calculate = () ->
    $scope.totaal = 0
    for d in $scope.dranken
      if d.aantal != 0 && d.aantal != ""
        $scope.totaal = $scope.totaal + (d.prijs * d.aantal)
        $scope.totaal = Math.round($scope.totaal * 100) / 100

  $scope.reset = () ->
      $scope.totaal = 0
      $scope.bestelling = {}
      for d in $scope.dranken
        d.aantal = 0

  Array::unique = ->
    output = {}
    output[@[key]] = @[key] for key in [0...@length]
    value for key, value of output


  $scope.submit = () ->
    $scope.bestelling.dranken = []
    $scope.bestelling.categorien = []
    if $scope.totaal > 0
      for d in $scope.dranken
        if d.aantal > 0
          drank = {}
          drank.naam = d.naam
          drank.aantal = d.aantal
          drank.categorie = d.categorie
          $scope.bestelling.categorien.push(d.categorie)
          $scope.bestelling.dranken.push(drank)
      $scope.bestelling.status = "in verwerking"
      $scope.bestelling.totaal = $scope.totaal
      $scope.bestelling.categorien = $scope.bestelling.categorien.unique()
      $scope.bestelling.verkoper = $scope.selectedOber
      date = new Date()
      $scope.bestelling.datum = date.toISOString()
      $http.post('/mongo/bestelling', $scope.bestelling).then( (response) ->
        console.log(response)
      )
      $scope.success = "Uw bestelling is geplaatst"
      $timeout( () ->
             $scope.success = ""
            , 3000)
      $scope.reset()

      console.log($scope.bestelling)
    else
      $scope.error = "Bestelling bevat geen dranken"
      $timeout( () ->
       $scope.error = ""
      , 3000)

BestellingController = ($scope, $http, $window) ->

  $scope.bestellingenEven = []
  $scope.bestellingenOdd = []
  $scope.ids = []

  $scope.init = () ->
    $http.get("/mongo/bestellingen/all")
    .success (data, status, headers, config) ->
      for b in data
        publish(b)
      createWS()
      return
    .error (data, status, headers, config) ->
      console.error("Fout bij het ophalen van de bestellingen")
      createWS()
      return
    return

  createWS = () ->
    ws = new WebSocket("ws://#{location.host}/mongo/bestellingen")
    ws.onopen = () ->
      console.info("Connected to websocket")
    ws.onmessage = (message) ->
      console.debug("Received message with data " + message.data)
      bestellingen = $scope.$apply(() -> publish(JSON.parse(message.data)))
      return
    return

  publish = (bestelling) ->
    if $scope.ids.lastIndexOf(bestelling._id.$oid) < 0
      $scope.ids.push(bestelling._id.$oid)
      console.log("Publishing bestelling")
      return unless bestelling
      if $scope.bestellingenOdd.length < $scope.bestellingenEven.length
        $scope.bestellingenOdd.push(bestelling)
      else
        $scope.bestellingenEven.push(bestelling)

  $scope.pickUp = (bestelling) ->
    $http.post("/mongo/handled", bestelling).then((response) ->
     console.log(response.data)
     $window.location.reload();
    )



CategorieController = ($scope, $http, $window) ->

  $scope.bestellingenEven = []
  $scope.bestellingenOdd = []
  $scope.ids = []

  $scope.init = (categorie) ->
    $http.get("/mongo/bestellingen/all/" + categorie)
    .success (data, status, headers, config) ->
      for b in data
        publish(b)
      createWS(categorie)
      createUpdatesWS(categorie)
      return
    .error (data, status, headers, config) ->
      console.error("Fout bij het ophalen van de bestellingen")
      createWS()
      createUpdatesWS()
      return
    return

  $scope.isLast = (bool, position) ->
    evenLength = $scope.bestellingenEven.length
    oddLength = $scope.bestellingenOdd.length
    if position == "even" && bool && evenLength > oddLength
      return true
    if position == "odd" && bool && oddLength >= evenLength
      return true
    return false


  createUpdatesWS = (categorie) ->
    ws = new WebSocket("ws://#{location.host}/mongo/connect/" + categorie)
    ws.onopen = () ->
      console.info("Connected to updates websocket")
    ws.onmessage = (message) ->
      console.info("Received message with data " + message.data)
      json = angular.fromJson(message.data)
      if json.reload
        $window.location.reload()


  createWS = (categorie) ->
    ws = new WebSocket("ws://#{location.host}/mongo/bestellingen/" + categorie)
    ws.onopen = () ->
      console.info("Connected to websocket")
    ws.onmessage = (message) ->
      console.info("Received message with data " + message.data)
      bestellingen = $scope.$apply(() -> publish(JSON.parse(message.data)))
      return
    return

  publish = (bestelling) ->
    if $scope.ids.lastIndexOf(bestelling._id.$oid) < 0
      $scope.ids.push(bestelling._id.$oid)
      console.log("Publishing bestelling")
      return unless bestelling
      if $scope.bestellingenOdd.length < $scope.bestellingenEven.length
        $scope.bestellingenOdd.push(bestelling)
      else
        $scope.bestellingenEven.push(bestelling)



angular.module("kassaModule", ["ui.bootstrap"])
  .controller("StartController", StartController)
  .controller("BestellingController", BestellingController)
  .controller("CategorieController", CategorieController)
