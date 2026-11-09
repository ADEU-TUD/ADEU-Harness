"""Pools of invented-data building blocks for the training texts (AP-67, Stufe b).

First names and surnames are common names of people living in Germany; the combinations are random, so no real person
is meant. Public persons, authors, institutions and places are public on purpose: the model has to learn to leave them.
Also reads the name lists of the app (``Names`` in PiiDetector.kt).
"""
from __future__ import annotations

import random
import re
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]


def _words(s: str) -> list[str]:
    return [w.replace("_", " ") for w in s.split()]


FIRST_W = _words("""
Anna Emma Mia Hanna Lea Lena Lina Leni Mila Marie Maria Clara Sophie Sofia Luisa Frieda Greta Emilia Amelie Charlotte
Johanna Lotta Nele Paula Maja Mathilda Leonie Laura Lara Sarah Julia Lisa Katharina Jana Nina Elena Helena Viktoria Ella
Ida Pia Merle Thea Alina Annika Carla Celina Emily Finja Fiona Isabell Jasmin Josephine Lilly Magdalena Marlene Mira Nora
Olivia Rosalie Stella Tessa Valentina Vanessa Wilma Zoe Ronja Elif Zeynep Fatma Leyla Amira Aylin Mariam Sabine Petra
Claudia Susanne Andrea Monika Birgit Kerstin Heike Anja Katrin Nicole Stefanie Silke Ines Ulrike Christine Barbara
Gabriele Ute Doris Karin Renate Ursula Sandra Melanie Daniela Tanja Simone Jessica Jennifer Franziska Juliane Antje Annett
Peggy Mandy Romy Anke Martina Manuela Cornelia Elke Bettina Beate Dagmar Angelika Brigitte Elisabeth Eva Ingrid Jutta
Marion Regina Sigrid Sylvia Uta Veronika Yvonne Diana Janine Jacqueline Theresa Lucia Hedwig Gisela Erika Martha Linda
Anne Annette Constanze Wiebke Frauke Gesa Imke Silja Birte Britta Doreen Grit Kati Kathleen Madlen Manja Nadine Nadja
Nancy Ramona Sindy Susann Jenny Heidi Helga Hannelore Ilse Inge Edith Gerda Gudrun Ruth Rita Helene Henriette Carola Katja
Larissa Lydia Mareike Maike Miriam Rebecca Ricarda Sabrina Saskia Selina Svenja Tamara Tina Verena Viola Alicia Alexandra
Antonia Bianca Caroline Chiara Denise Elisa Felicitas Femke Fenja Frida Gloria Jette Jule Kira Liv Mara Marla Mieke Milla
Nelly Noemi Philippa Rieke Rosa Smilla Tilda Yara Ylvi Juna Luna Alma Mina Milena Jolina Liana Aleyna Hira Ecrin Defne
Azra Eslem Medina Melek Esra Emine Hatice Meryem Derya Selin Buse Büşra Merve Amina Aya Nour Rania Yasmin Dalia Hiba
Malak Salma Sana Aisha Fatima Zahra Layla Habiba Olga Tatjana Natalia Svetlana Irina Jelena Galina Oksana Anastasia
Daria Polina Sofiia Kateryna Olena Iryna Yulia Mariya Tetiana Halyna Zofia Agnieszka Katarzyna Joanna Ewa Aleksandra
Dorota Justyna Karolina Weronika Kinga Paulina Linh Huong Hoa Ngoc Trang Thao Mai Lan Thu Hanh Xiaoling Mei Yan Lijuan
Ama Abena Adaeze Chiamaka Ngozi Amara Nia Aminata Fatou Mariama Lucía Carmen Isabel Elena Giulia Francesca Chiara Alessia
Ioana Andreea Elena Bianca Maria-Luisa Anna-Lena Lea-Marie Emma-Sophie Lena-Marie Mia-Sophie Sarah-Jane Kunigunde Hildegard
Mechthild Walburga Leokadia Swantje Hauke Ilka Hilke Kirsten Dörte Inken Tomke Wenke Ylva Saga Freya Ebba Liva Signe
Nayeli Ximena Valeria Camila Yuki Hana Sakura Min-Ji Ji-Woo Seo-Yeon Priya Ananya Aditi Kavya Shirin Parisa Mahsa Niloufar
Roya Golnar Dilan Rojin Berfin Zilan Hevin Gülistan Neslihan Songül Ayten Şeyma Kübra Rabia Tuba Jovana Milica Marija Ana
""")

FIRST_M = _words("""
Noah Leon Paul Ben Finn Elias Felix Luca Lukas Henry Emil Theo Jonas Louis Anton Max Liam Matteo Oskar Moritz Jakob Leo
Tim Tom Niklas Julian David Samuel Alexander Maximilian Philipp Simon Fabian Florian Sebastian Daniel Michael Thomas
Andreas Stefan Steffen Christian Martin Peter Markus Matthias Tobias Johannes Jonathan Benjamin Erik Ole Mats Milan Karl
Friedrich Konstantin Vincent Valentin Rafael Levi Ali Mohammed Yusuf Emir Ahmet Mehmet Mustafa Omar Hamza Ibrahim Jürgen
Uwe Ralf Dirk Jörg Torsten Holger Frank Bernd Wolfgang Klaus Dieter Heinz Rainer Gerd Horst Manfred Werner Günter Helmut
Hans Lars Sven Kevin Dennis Marcel Patrick Robert René Jens Mirko Ronny Rico Enrico Toni Gustav Arne Bastian Malte Hannes
Till Nils Lennart Linus Mattis Joshua Aaron Adam Jannik Kilian Malik Marlon Nico Pascal Robin Ruben Fritz Hugo Wilhelm
Otto Georg Heinrich Hermann Ludwig Ulrich Volker Detlef Hartmut Reinhard Norbert Joachim Gerhard Bernhard Rüdiger Axel
Olaf Carsten Ingo Marco Mario Oliver Sascha Timo Tino Thilo Udo Dmitri Igor Sergej Piotr Jan Kai Jasper Joris Jonte Mika
Bjarne Fiete Jarne Keno Lasse Tjark Piet Lio Elian Noel Jona Joel Bennet Juri Henrik Hendrik Frederik Dominik Arthur
Timur Theodor Konrad Leopold Albert Gabriel Laurin Lorenz Benedikt Korbinian Quirin Severin Kaspar Damian Adrian Marius
Kian Elyas Yasin Emre Kerem Deniz Eren Efe Mert Berat Baran Burak Furkan Kaan Onur Serkan Tarik Karim Khalid Hassan
Hüseyin Osman Bilal Amir Samir Sami Rami Ahmad Mahmoud Youssef Abdullah Idris Ilias Ilja Artjom Maxim Nikita Daniil Bohdan
Taras Andrij Oleksandr Dmytro Mykola Mykyta Wojciech Krzysztof Tomasz Paweł Marek Grzegorz Łukasz Jakub Mateusz Kacper
Szymon Bartosz Filip Dawid Kamil Michał Jiří Petr Pavel Tomáš Ondřej Dang Trung Phuc Khanh Bao Tien Vinh Minh Duc Tuan
Quang Thanh Wei Jun Hao Yang Kofi Kwame Kwabena Chidi Emeka Obinna Amadou Moussa Ibrahima Mamadou Diego Mateo Alejandro
Pablo Javier Lorenzo Giovanni Francesco Andrei Mihai Bogdan Ion Stefan Nikola Luka Marko Ivan Dragan Arjun Rohan Vikram
Reza Arash Kian Dariush Farid Navid Siamak Azad Rojhat Serdar Zana Baran Hevar Siyar Achim Alfred Armin Arno Benno Björn
Bodo Burkhard Christoph Clemens Cornelius Dietmar Eberhard Edgar Egon Erwin Ferdinand Franz Gernot Gottfried Gregor Guido
Gunnar Harald Heiko Helge Herbert Hubert Jochen Josef Kurt Lothar Lutz Mike Nikolai Norman Roland Rolf Rudolf Siegfried
Silvio Thoralf Tilman Ulf Veit Walter Willi Winfried Wolfram Dustin Justin Marvin Maik Danny Sandro Rocco Siegmar
Hauke Thore Lenni Tobi Jens-Uwe Kai-Uwe Hans-Peter Karl-Heinz Jan-Niklas Ben-Luca Paul-Luis Lars-Ole
""")

SURNAMES = _words("""
Müller Schmidt Schneider Fischer Weber Meyer Meier Wagner Becker Schulz Schulze Hoffmann Schäfer Koch Bauer Richter Klein
Wolf Schröder Neumann Schwarz Zimmermann Braun Krüger Hofmann Hartmann Lange Schmitt Werner Schmitz Krause Lehmann Schmid
Köhler Herrmann König Walter Huber Kaiser Fuchs Peters Lang Scholz Möller Weiß Jung Hahn Schubert Vogel Friedrich Keller
Günther Frank Berger Winkler Roth Beck Lorenz Baumann Franke Albrecht Schuster Simon Ludwig Böhm Winter Kraus Martin
Schumacher Krämer Vogt Stein Jäger Otto Sommer Groß Seidel Heinrich Brandt Haas Schreiber Graf Schulte Dietrich Ziegler
Kuhn Kühn Pohl Engel Horn Busch Bergmann Thomas Voigt Sauer Arnold Wolff Pfeiffer Lindner Ernst Hübner Kretschmer
Kretzschmar Ullrich Ulbrich Wendt Hennig Göbel Schütz Schütze Ackermann Böttcher Brückner Busse Dittrich Ebert Ehrlich
Fiedler Förster Frenzel Fritzsche Geißler Gerlach Glaser Götz Hanke Heinze Herzog Hesse Hildebrandt Hoppe Jahn Kaufmann
Kirchner Kluge Knoll Kolb Kraft Kunze Lenz Lindemann Löffler Marx Mertens Michel Mohr Morgenstern Nagel Naumann Nitsche
Oswald Petzold Pietsch Preuß Rau Reichel Reimann Reinhardt Riedel Rösler Rudolph Sachse Schade Scheffler Schilling
Schindler Schlegel Schmiedel Schönfeld Schramm Seifert Seiler Stark Steinbach Strauß Thiele Thiel Trautmann Unger Vetter
Walther Weigel Weise Wenzel Wiesner Wilhelm Wolter Zander Zeidler Funke Bach Barth Baum Beyer Bock Dörr Eckert Fink
Gärtner Gebhardt Gruber Haase Hauser Henke Hertel Heuer Hinz Hummel Jansen Kemper Kern Keßler Kirsch Klose Knapp Krebs
Kroll Kurz Maurer Menzel Metzger Möbius Nickel Oppermann Pape Raab Ritter Rose Sander Schott Schwab Siebert Stahl Stoll
Vollmer Voß Weidner Wirth Witt Wittig Zimmer Arnhold Bachmann Bähr Bartsch Baumgart Behrendt Benedix Bernhardt Beutler
Bienert Blümel Bormann Brendel Buchheim Däbritz Dathe Demmler Drechsler Eichler Eisold Ermisch Escher Feller Fichtner
Findeisen Flechsig Freitag Friebel Fröde Gäbler Gaitzsch Gierth Gläser Glöckner Goldammer Görner Gottschalk Grafe Gründel
Günzel Haubold Hauptmann Heidrich Heinig Helbig Hempel Hentschel Herold Heymann Hiller Hofmeister Höhne Hoyer Hutschenreuther
Ihle Jähnig Jakob Jeschke Junghans Kaden Kalkbrenner Kämpfe Kanis Kirsten Klaus Kleinert Klotz Knöfel Kockel Köckeritz
Kolbe Korb Kotte Kraska Kreher Kretzschmer Kubitz Kühnel Kummer Kunath Lange-Wagner Laube Leder Leistner Lieberwirth Liebscher
Löbel Lohse Ludewig Lungwitz Mädler Mattick Mauersberger Meinel Merkel Mieth Mittag Mühle Münch Nestler Neubauer Noack
Nöthlich Ohme Olbrich Opitz Pech Pfennig Pietzsch Pilz Prasse Preißler Pursche Quaas Rabe Rasch Rehn Reißig Richter-Lorenz
Riemer Ritschel Rößler Röthig Rothe Rümmler Salzmann Sandig Schaarschmidt Scharf Schenk Schiller-Böhme Schlimpert Schmieder
Schöne Schreiter Schuricht Schwenke Seydel Siegel Sieber Spitzner Stange Steglich Stelzner Stöckel Stolle Teubner Thieme
Tietze Tittel Trepte Uhlig-Schreiber Ulrich Vieweg Vollrath Wachs Walde Weinhold Weiske Wendler Wilde Winkler-Fuchs Wolle
Wunderlich Zabel Zöphel Zschau Zschoge Zücker Nowak Kowalski Kowalczyk Wiśniewski Wójcik Kamiński Lewandowski Zieliński
Szymański Woźniak Dąbrowski Kozłowski Jankowski Mazur Krawczyk Piotrowski Grabowski Pawłowski Michalski Nowakowski
Novák Svoboda Novotný Dvořák Černý Procházka Kučera Veselý Horák Němec Pokorný Marek Král Šťastný Yılmaz Kaya Demir
Şahin Çelik Yıldız Yıldırım Öztürk Aydın Özdemir Arslan Doğan Kılıç Aslan Çetin Kara Koç Kurt Özkan Şimşek Polat Korkmaz
Erdoğan Aksoy Güneş Bozkurt Keskin Kaplan Tekin Avcı Ateş Bulut Duman Ekinci Güler Işık Karaca Sarı Tunç Ünal Yavuz
Nguyen Tran Le Pham Hoang Huynh Phan Vu Vo Dang Bui Do Ho Ngo Duong Ly Ivanov Petrov Smirnov Popov Kuznetsov Sokolov
Volkov Morozov Novikov Fedorov Lebedev Kozlov Pavlov Semenov Golubev Shevchenko Kovalenko Bondarenko Tkachenko Kravchenko
Boyko Melnyk Oliynyk Lysenko Marchenko Rudenko Savchenko Moroz Hrytsenko Haddad Khalil Hassan Hussein Ahmadi Mohammadi
Hosseini Rahimi Karimi Rezaei Mansour Saleh Nasser Ibrahim Abdullah Al-Hassan Al-Masri El-Amin Abdi Osman Yusuf Hamid
Sultani Noori Haidari Sadat Stanekzai Popescu Ionescu Popa Stan Dumitru Constantin Horvat Kovačević Petrović Jovanović
Nikolić Hoxha Krasniqi Berisha Gashi Papadopoulos Georgiou Rossi Russo Ferrari Esposito Bianchi Romano Ricci Marino Greco
Garcia Fernandez Lopez Martinez Gonzalez Rodriguez Sanchez Silva Santos Oliveira Pereira Mensah Owusu Boateng Asante
Okafor Okonkwo Adeyemi Eze Nwosu Diallo Traoré Camara Ndiaye Kamara Wang Li Zhang Liu Chen Yang Huang Zhao Wu Zhou Kim
Park Lee Choi Tanaka Suzuki Sato Patel Sharma Singh Gupta von_Bülow von_der_Heide van_Dyk de_Vries zu_Lynar
Schulze-Wendt Müller-Lüdenscheidt Kraus-Hennig Becker-Rahimi Fischer-Yılmaz Schmidt-Nowak
""")

# Pieces for invented surnames in the style of Saxony and Lusatia ("Zschuckelt", "Plötzner") – the model must also
# find names that are in no list. (Not taken from the test part of the Messkorpus.)
_SURNAME_HEADS = _words("""
Bötz Dietz Fleisch Gött Hörn Jäsch Kirst Krausch Lieb Löb Möck Mütz Nöb Pötz Rösch Rüh Säck Schlenk Schmöl Stiehl Tietz
Ulb Völk Wied Zieh Zöll Brös Dräg Eul Fiß Görl Grütz Häs Hölz Jähn Knaut Kühl Lätz Mied Nietz Pfaff Plötz Rehn Ruschk
Schäd Sporb Thär Trög Wäch Weidl Zschuck Zschorn Zscheg Zschunk Wolfr Gräf Hünd Kleinsch Lohs Mös Nestl Oertl Prell
Quell Rothl Seid Tänn Ullm Vög Wals Zeuk Bürg Dörf Esch Feg Gless Herrm Ill Joch Kerst Lorb Mähl Nebel Öhm Pursch
""")
_SURNAME_TAILS = _words("""
schmann mann ner ler ig sch sche ke ker old ich itz ow e er el ert ling hold wald hardt mer ske elt ing
""")


def invented_surname(rng: random.Random) -> str:
    head = rng.choice(_SURNAME_HEADS)
    tail = rng.choice(_SURNAME_TAILS)
    if head[-1] == tail[0]:
        tail = tail[1:] or "e"
    return head + tail


def _app_names() -> tuple[list[str], list[str]]:
    """``Names.first`` and ``Names.last`` of the app (PiiDetector.kt)."""
    src = (REPO / "app/src/main/java/io/github/thepytales/llmhub/data/PiiDetector.kt").read_text(encoding="utf-8")
    block = src[src.index("object Names"):]
    parts = re.findall(r'"""(.*?)"""', block, re.S)
    return parts[0].split(), parts[1].split()


APP_FIRST, APP_LAST = _app_names()

# Places: Saxony, districts of the big cities, other towns in Germany, towns abroad (origin of families).
PLACES = _words("""
Pirna Meißen Freital Radebeul Bautzen Görlitz Zittau Riesa Großenhain Kamenz Hoyerswerda Heidenau Sebnitz Dippoldiswalde
Coswig Moritzburg Ottendorf-Okrilla Radeberg Pulsnitz Bad_Schandau Königstein Altenberg Glashütte Weißwasser Niesky Löbau
Plauen Zwickau Chemnitz Freiberg Döbeln Torgau Delitzsch Eilenburg Grimma Wurzen Oschatz Borna Markkleeberg Leipzig
Dresden Annaberg-Buchholz Aue Schwarzenberg Marienberg Olbernhau Stollberg Limbach-Oberfrohna Glauchau Crimmitschau
Werdau Reichenbach Auerbach Oelsnitz Mittweida Rochlitz Flöha Hainichen Roßwein Nossen Wilsdruff Tharandt Kreischa
Bannewitz Dohna Neustadt_in_Sachsen Stolpen Bischofswerda Wilthen Kirschau Neugersdorf Ebersbach Seifhennersdorf Oybin
Herrnhut Bernstadt Ostritz Rothenburg Bad_Muskau Lauta Bernsdorf Königsbrück Ottendorf Arnsdorf Ullersdorf Weinböhla
Lommatzsch Nünchritz Gröditz Strehla Mügeln Colditz Bad_Lausick Geithain Frohburg Regis-Breitingen Zwenkau Taucha
Schkeuditz Brandis Naunhof Bad_Düben Klingenthal Markneukirchen Adorf Bad_Elster Treuen Lengenfeld Kirchberg Wilkau-Haßlau
Gorbitz Prohlis Pieschen Johannstadt Striesen Blasewitz Klotzsche Loschwitz Cotta Löbtau Leuben Laubegast Plauen Strehlen
Seidnitz Tolkewitz Gruna Reick Leubnitz Kaditz Mickten Trachau Hellerau Weixdorf Langebrück Bühlau Weißig Gittersee
Coschütz Zschachwitz Niedersedlitz Lockwitz Briesnitz Omsewitz Kemnitz Übigau Wilschdorf Rähnitz Dresden-Neustadt
Grünau Paunsdorf Connewitz Plagwitz Lindenau Gohlis Reudnitz Schönefeld Mockau Möckern Stötteritz Probstheida Lößnig
Kaßberg Sonnenberg Hutholz Markersdorf Gablenz Bernsdorf Schloßchemnitz Ebersdorf Hilbersdorf Rabenstein
Berlin Hamburg München Köln Frankfurt Stuttgart Düsseldorf Hannover Nürnberg Bremen Magdeburg Halle Erfurt Jena Weimar
Gera Cottbus Potsdam Rostock Schwerin Kiel Lübeck Kassel Göttingen Würzburg Regensburg Passau Freiburg Konstanz Mainz
Bonn Münster Bielefeld Dortmund Essen Duisburg Wuppertal Aachen Saarbrücken Augsburg Ulm Heidelberg Mannheim Karlsruhe
Charkiw Kyjiw Odessa Lwiw Dnipro Saporischschja Mariupol Aleppo Damaskus Homs Idlib Kabul Herat Masar-e_Scharif Teheran
Bagdad Mossul Erbil Istanbul Ankara Izmir Diyarbakır Gaziantep Breslau Wrocław Krakau Posen Liberec Prag Ústí Brünn
Hanoi Ho-Chi-Minh-Stadt Hải_Phòng Accra Lagos Kumasi Asmara Mogadischu Tiflis Jerewan Bukarest Sofia Belgrad Pristina
""")

#: Places that are named in public contexts – the model sees the same names with and without personal reference.
PUBLIC_PLACES = _words("""
Dresden Leipzig Chemnitz Berlin Hamburg München Köln Frankfurt Bremen Weimar Erfurt Jena Halle Magdeburg Potsdam Bonn
Sachsen Thüringen Bayern Brandenburg Hessen Niedersachsen Nordrhein-Westfalen Baden-Württemberg Sachsen-Anhalt
Deutschland Österreich Schweiz Finnland Schweden Norwegen Dänemark Polen Tschechien Frankreich Italien Spanien England
Kanada Japan China Singapur Estland Ostsachsen Lausitz Erzgebirge Vogtland Elbtal Sächsische_Schweiz Europa Brüssel
Wien Zürich Paris London Rom Prag Breslau Wittenberg Görlitz Meißen Bautzen Freiberg Moritzburg Pillnitz Königstein
""")

AUTHORS = _words("""
Klafki Meyer Helmke Hattie Terhart Kunter Baumert Lipowsky Reusser Oser Bourdieu Luhmann Habermas Adorno Kant Rousseau
Herbart Freire Bandura Deci Ryan Bloom Krathwohl Anderson Wember Prengel Wocken Feuser Hinz Boban Piaget Wygotski Vygotsky
Dewey Montessori Pestalozzi Fröbel Comenius Humboldt Schleiermacher Nohl Flitner Benner Mollenhauer Giesecke Tenorth
Fend Hurrelmann Kounin Brophy Slavin Johnson Kyndt Mayring Kuckartz Bohnsack Strauss Corbin Glaser Flick Bortz Döring
Rost Eid Klieme Prenzel Köller Pant Stanat Reiss Weinert Renkl Seidel Shulman Blömeke Kaiser Neubrand Krauss Lindmeier
Hascher Holtappels Rolff Fullan Hargreaves Lortie Ziehe Hentig Rumpf Wagenschein Aebli Gudjons Jank Kiper Mischke Wiater
Ditton Maaz Becker Hasselhorn Schneider Gold Hartmann Zierer Wellenreuther Kirschner Sweller Mayer Paas Wild Möller
Krapp Pekrun Schiefele Rheinberg Dweck Eccles Wigfield Hamre Pianta Fauth Praetorius Klusmann Voss Richter Stürmer
Trautwein Lüdtke Nagengast Marsh Seaton Hannover Schwippert Tillmann Wischer Sturm Wiedenhorn Budde Rabenstein Breidenstein
Kelle Gomolla Radtke Mecheril Dirim Gogolin Lange Rothe Ahrenholz Oomen-Welke Grießhaber Lütke Tajmel Kniffka
Hericks Helsper Combe Kolbe Oevermann Koch-Priewe Rotter Ackeren Bellmann Bremm Dedering Böttcher Altrichter Posch
Maag_Merki Emmerich Spillebeen Hallinger Leithwood Bryk Timperley Shute Black Wiliam Sadler Brookhart Nicol Narciss
""")

#: Full names of public persons (politics, culture, science, sport, history) and figures from books.
PUBLIC_PERSONS = _words("""
Angela_Merkel Olaf_Scholz Friedrich_Merz Michael_Kretschmer Frank-Walter_Steinmeier Helmut_Kohl Willy_Brandt
Konrad_Adenauer Gerhard_Schröder Annalena_Baerbock Robert_Habeck Christian_Lindner Kurt_Biedenkopf Joachim_Gauck
Johann_Wolfgang_von_Goethe Friedrich_Schiller Gotthold_Ephraim_Lessing Heinrich_Heine Theodor_Fontane Thomas_Mann
Bertolt_Brecht Franz_Kafka Hermann_Hesse Erich_Kästner Michael_Ende Cornelia_Funke Astrid_Lindgren Otfried_Preußler
Paul_Maar Kirsten_Boie Janosch Christine_Nöstlinger Wolfgang_Herrndorf Juli_Zeh Daniel_Kehlmann Herta_Müller
Albert_Einstein Marie_Curie Lise_Meitner Alexander_von_Humboldt Wilhelm_von_Humboldt Charles_Darwin Isaac_Newton
Johann_Sebastian_Bach Ludwig_van_Beethoven Wolfgang_Amadeus_Mozart Richard_Wagner Clara_Schumann Robert_Schumann
Felix_Mendelssohn_Bartholdy Käthe_Kollwitz Caspar_David_Friedrich Albrecht_Dürer Frida_Kahlo Pablo_Picasso
Martin_Luther Martin_Luther_King Nelson_Mandela Mahatma_Gandhi Sophie_Scholl Anne_Frank Rosa_Parks Malala_Yousafzai
Greta_Thunberg Elon_Musk Taylor_Swift Helene_Fischer Thomas_Müller Jürgen_Klopp Toni_Kroos Manuel_Neuer Dirk_Nowitzki
Steffi_Graf Boris_Becker Michael_Schumacher Katarina_Witt Jens_Weißflog Lena_Meyer-Landrut Herbert_Grönemeyer
Udo_Lindenberg Nena Peter_Maffay Uwe_Steimle Jan_Josef_Liefers Til_Schweiger Iris_Berben Harald_Lesch Ranga_Yogeshwar
Mai_Thi_Nguyen-Kim Eckart_von_Hirschhausen Karl_Marx Friedrich_Engels Rosa_Luxemburg August_der_Starke Napoleon
Julius_Caesar Kleopatra Karl_der_Große Otto_von_Bismarck Kaiser_Wilhelm Papst_Franziskus Dalai_Lama Barack_Obama
Joe_Biden Donald_Trump Emmanuel_Macron Wladimir_Putin Wolodymyr_Selenskyj Harry_Potter Hermine_Granger Pippi_Langstrumpf
Michel_aus_Lönneberga Karlsson_vom_Dach Emil_Tischbein Momo Jim_Knopf Lukas_der_Lokomotivführer Das_Sams Räuber_Hotzenplotz
Kasperle Max_und_Moritz Struwwelpeter Rotkäppchen Hänsel_und_Gretel Rumpelstilzchen Sherlock_Holmes Pumuckl Meister_Eder
Benjamin_Blümchen Bibi_Blocksberg Conni Petterson_und_Findus Der_kleine_Prinz Winnetou Old_Shatterhand Tom_Sawyer
Maria_Montessori Johann_Heinrich_Pestalozzi Friedrich_Fröbel Jan_Amos_Comenius Wolfgang_Klafki Hilbert_Meyer John_Hattie
Andreas_Helmke Mareike_Kunter Jürgen_Baumert Olaf_Köller Jutta_Allmendinger Aladin_El-Mafaalani Remo_Largo Jesper_Juul
""")

PUBLIC_INSTITUTIONS = _words("""
TU_Dresden Universität_Leipzig TU_Chemnitz TU_Bergakademie_Freiberg Humboldt-Universität Freie_Universität_Berlin
Kultusministerkonferenz KMK Kultusministerium Sächsisches_Staatsministerium_für_Kultus Landesamt_für_Schule_und_Bildung
LaSuB Bundesministerium_für_Bildung_und_Forschung Max-Planck-Institut_für_Bildungsforschung DIPF IQB IPN
Deutsches_Jugendinstitut Goethe-Institut Robert_Koch-Institut Statistisches_Bundesamt Destatis Statistisches_Landesamt
OECD UNESCO UNICEF Deutsche_Forschungsgemeinschaft DFG Studienstiftung SLUB Sächsische_Landesbibliothek
Deutsches_Hygiene-Museum Albertinum Semperoper Frauenkirche Kreuzkirche Dresdner_Philharmonie Staatliche_Kunstsammlungen
Bundeszentrale_für_politische_Bildung Bertelsmann_Stiftung Robert_Bosch_Stiftung Deutsche_Telekom_Stiftung
Deutscher_Lehrerverband GEW Philologenverband Studentenwerk_Dresden ZIH Sächsische_Zeitung Leipziger_Volkszeitung
Bundesagentur_für_Arbeit Jugendamt Landratsamt Stadtverwaltung Polizei Bundeswehr Deutsche_Bahn Volkshochschule
Universitätsklinikum Fraunhofer-Institut Leibniz-Institut Helmholtz-Zentrum Senckenberg Zoo_Dresden Stadtbibliothek
Deutsche_Gesellschaft_für_Erziehungswissenschaft DGfE GEBF Klassik_Stiftung_Weimar Gedenkstätte_Bautzner_Straße
""")

WORKS = _words("""
Emil_und_die_Detektive Das_doppelte_Lottchen Momo Die_unendliche_Geschichte Jim_Knopf_und_Lukas_der_Lokomotivführer
Pippi_Langstrumpf Die_Kinder_aus_Bullerbü Ronja_Räubertochter Krabat Der_Räuber_Hotzenplotz Die_kleine_Hexe
Tintenherz Rico,_Oskar_und_die_Tieferschatten Tschick Der_Vorleser Die_Welle Woyzeck Faust Nathan_der_Weise
Kabale_und_Liebe Effi_Briest Buddenbrooks Der_Steppenwolf Siddhartha Das_Tagebuch_der_Anne_Frank Max_und_Moritz
Harry_Potter Gregs_Tagebuch Die_drei_??? TKKG Hanni_und_Nanni Wilhelm_Tell Maria_Stuart Romeo_und_Julia
Der_Sandmann Michael_Kohlhaas Iphigenie_auf_Tauris Die_Leiden_des_jungen_Werther Das_Sams Der_kleine_Prinz
""")

#: Fantasy names of day-care centres, after-school care and clubs.
KITA_NAMES = _words("""
Sonnenschein Pusteblume Regenbogen Spatzennest Wichtelland Zwergenland Kunterbunt Elbpiraten Wiesenwichtel Löwenzahn
Sternschnuppe Kleine_Strolche Glühwürmchen Marienkäfer Purzelbaum Schatzkiste Rasselbande Sonnenblume Waldwichtel
Elbflitzer Spielkiste Kinderland Pfiffikus Sausewind Abenteuerland Wirbelwind Zauberwald Bärenhöhle Grashüpfer Seepferdchen
Schmetterling Mäusenest Krümelkiste Villa_Kunterbunt Am_Elbhang Am_Großen_Garten Pünktchen Rappelkiste Knirpsenland
Wolkenschloss Zwergenburg Traumland Sonnenkäfer Kleeblatt Hummelnest Eichhörnchen Lummerland Pinguin Wiesengrund
""")

#: Words in "Grundschule am …", "Oberschule an der …".
SCHOOL_PLACES = _words("""
am_Park am_Wasserturm an_der_Elbe am_Lindenhof am_Rosengarten am_Bürgerwiese an_der_Weißeritz am_Stadtwald am_Schloss
am_Hufeisen an_der_Heide am_Alberthafen am_Elbbogen am_Mühlgraben am_Sonnenhang an_den_Linden am_Windberg am_Kirchberg
""")

#: Famous persons in school names ("Erich-Kästner-Grundschule", "Gymnasium Marie Curie").
SCHOOL_PATRONS = _words("""
Erich-Kästner Goethe Schiller Marie-Curie Albert-Schweitzer Pestalozzi Lessing Heinrich-Heine Martin-Luther Humboldt
Romain-Rolland Bertolt-Brecht Anne-Frank Sophie-Scholl Geschwister-Scholl Käthe-Kollwitz Gotthold-Ephraim-Lessing
Johann-Gottfried-Herder Friedrich-Fröbel Montessori Astrid-Lindgren Janusz-Korczak Hans-Christian-Andersen Clara-Zetkin
Adam-Ries Gottfried-Semper Carl-Zeiss Ludwig-Richter Kreuzschule Vitzthum Dürer Lise-Meitner Otto-Lilienthal
""")

ASSOCIATIONS = _words("""
Kinderland Mosaik Kompass Lichtblick Brückenschlag Weitblick Zukunft Miteinander Starthilfe Lernwerkstatt Nachbarschaft
Familienhafen Treffpunkt Sprungbrett Aufwind Leuchtturm Querbeet Wurzelwerk Kreativwerkstatt Sternwarte Freiraum
""")

SUBJECTS = _words("Deutsch Mathe Mathematik Englisch Sachunterricht Geschichte Geografie Biologie Physik Chemie Kunst Musik "
                  "Sport Ethik Religion Informatik Französisch Russisch Latein Spanisch WTH Gemeinschaftskunde Sorbisch")

COLORS = _words("Blau-Weiß Rot-Weiß Grün-Weiß Schwarz-Gelb Lok Einheit Motor Chemie Empor Stahl Fortuna Concordia Germania")


def place(rng: random.Random) -> str:
    return rng.choice(PLACES)


def institution(rng: random.Random, kind: str | None = None) -> str:
    """An invented school, day-care centre, practice or club – "32. Grundschule", "Kita Sonnenschein", …"""
    kind = kind or rng.choice(["SCHULE", "SCHULE", "SCHULE", "KITA", "KITA", "PRAXIS", "VEREIN"])
    p = place(rng)
    if kind == "SCHULE":
        return rng.choice([
            lambda: f"{rng.randint(1, 160)}. Grundschule",
            lambda: f"{rng.randint(1, 140)}. Oberschule",
            lambda: f"Grundschule {p}",
            lambda: f"Oberschule {p}",
            lambda: f"Gymnasium {p}",
            lambda: f"Grundschule {rng.choice(SCHOOL_PLACES)}",
            lambda: f"Oberschule {rng.choice(SCHOOL_PLACES)}",
            lambda: f"{rng.choice(SCHOOL_PATRONS)}-Gymnasium",
            lambda: f"{rng.choice(SCHOOL_PATRONS)}-Grundschule",
            lambda: f"{rng.choice(SCHOOL_PATRONS)}-Oberschule",
            lambda: f"Gymnasium {p}-{rng.choice(['Nord', 'Süd', 'West', 'Ost', 'Mitte'])}",
            lambda: f"Evangelische Grundschule {p}",
            lambda: f"Freie Schule {rng.choice(KITA_NAMES)}",
            lambda: f"Montessori-Schule {p}",
            lambda: f"Förderschule {p}",
            lambda: f"Förderzentrum {rng.choice(SCHOOL_PATRONS).replace('-', ' ')}",
            lambda: f"Berufliches Schulzentrum {p}",
            lambda: f"Waldorfschule {p}",
        ])()
    if kind == "KITA":
        n = rng.choice(KITA_NAMES)
        return rng.choice([
            lambda: f"Kita {n}", lambda: f"Kita „{n}“", lambda: f"Kindergarten {n}", lambda: f"Hort {n}",
            lambda: f"Kita {n} {p}", lambda: f"Kindertagesstätte {n}", lambda: f"Hort der {rng.randint(1, 160)}. Grundschule",
        ])()
    if kind == "PRAXIS":
        return rng.choice([
            lambda: f"Praxis Dr. {rng.choice(SURNAMES)}", lambda: f"Kinderarztpraxis Dr. {rng.choice(SURNAMES)}",
            lambda: f"Ergotherapiepraxis {rng.choice(KITA_NAMES)}", lambda: f"Logopädie {rng.choice(SURNAMES)}",
            lambda: f"Physiotherapie {rng.choice(SURNAMES)}", lambda: f"Beratungsstelle {rng.choice(ASSOCIATIONS)}",
        ])()
    return rng.choice([
        lambda: f"{rng.choice(ASSOCIATIONS)} e.V.", lambda: f"Verein {rng.choice(ASSOCIATIONS)}",
        lambda: f"Jugendhaus {rng.choice(ASSOCIATIONS)}", lambda: f"SV {rng.choice(COLORS)} {p}",
        lambda: f"Musikschule {rng.choice(KITA_NAMES)}", lambda: f"Jugendclub {rng.choice(ASSOCIATIONS)}",
        lambda: f"FSV {p}", lambda: f"Tanzschule {rng.choice(SURNAMES)}",
    ])()
