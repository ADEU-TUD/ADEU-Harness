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

#: More public persons: politics in Saxony and Germany, culture, media, research, sport.
PUBLIC_PERSONS += _words("""
Martin_Dulig Petra_Köpping Barbara_Klepsch Christian_Piwarz Dirk_Hilbert Burkhard_Jung Sven_Schulze Rainer_Haseloff
Bodo_Ramelow Manuela_Schwesig Markus_Söder Hendrik_Wüst Winfried_Kretschmann Karl_Lauterbach Bettina_Stark-Watzinger
Cem_Özdemir Hubertus_Heil Nancy_Faeser Boris_Pistorius Lars_Klingbeil Saskia_Esken Ricarda_Lang Sahra_Wagenknecht
Gregor_Gysi Wolfgang_Schäuble Norbert_Lammert Bärbel_Bas Julia_Klöckner Ursula_von_der_Leyen Roman_Herzog Horst_Köhler
Christian_Wulff Richard_von_Weizsäcker Walter_Ulbricht Erich_Honecker Michail_Gorbatschow John_F._Kennedy
Christa_Wolf Ingo_Schulze Uwe_Tellkamp Durs_Grünbein Erich_Loest Sarah_Kirsch Volker_Braun Christoph_Hein
Ursula_Poznanski Sebastian_Fitzek Charlotte_Link Nele_Neuhaus Isabel_Abedi
Max_Kruse Margit_Auer Alice_Pantermüller Rafik_Schami Saša_Stanišić Judith_Kerr Erich_Maria_Remarque Günter_Grass
Heinrich_Böll Siegfried_Lenz Patrick_Süskind Bernhard_Schlink Mirjam_Pressler Gudrun_Pausewang
Ernst_Jandl Joachim_Ringelnatz Wilhelm_Busch Heinrich_Hoffmann Ludwig_Bechstein Jacob_Grimm Wilhelm_Grimm
Gerhard_Richter Neo_Rauch Otto_Dix Max_Pechstein Ernst_Ludwig_Kirchner Paula_Modersohn-Becker Joseph_Beuys
Peter_Schreier Richard_Strauss Carl_Maria_von_Weber Heinrich_Schütz Georg_Friedrich_Händel Johannes_Brahms
Jan_Böhmermann Joko_Winterscheidt Klaas_Heufer-Umlauf Anke_Engelke Hape_Kerkeling Thomas_Gottschalk Günther_Jauch
Markus_Lanz Sandra_Maischberger Anne_Will Marietta_Slomka Claus_Kleber Ingo_Zamperoni Louis_Klamroth
Christian_Drosten Lothar_Wieler Hendrik_Streeck Alena_Buyx Harald_zur_Hausen Christiane_Nüsslein-Volhard
Stefan_Hell Reinhard_Genzel Benjamin_List Svante_Pääbo Emmanuelle_Charpentier Klaus_Hasselmann
Franziska_van_Almsick Magdalena_Neuner Laura_Dahlmeier Fabian_Hambüchen Robert_Harting Alexander_Zverev
Angelique_Kerber Sebastian_Vettel Joshua_Kimmich Jamal_Musiala Florian_Wirtz Leroy_Sané Ilkay_Gündogan
Lukas_Podolski Bastian_Schweinsteiger Miroslav_Klose Oliver_Kahn Franz_Beckenbauer Matthias_Sammer
Klaus_Zierer Ilka_Hoffmann Heinz-Elmar_Tenorth Dietrich_Benner Kai_Maaz Petra_Stanat
Manfred_Spitzer Gerald_Hüther Richard_David_Precht Bas_Kast Michael_Winterhoff
Pippi Ronja Krabat Hotzenplotz Petterson Findus Sams Grüffelo Gruffalo Pinocchio Heidi Biene_Maja Heidi_Klum
Kapitän_Blaubär Käpt'n_Sharky Mama_Muh Die_kleine_Raupe_Nimmersatt Pünktchen_und_Anton
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

#: Even more public persons (Nachtraining 10.11.2026): history, science, philosophy, literature, music, film, sport,
#: politics in Germany and abroad, education and media – so the model meets many public names it has to leave.
PUBLIC_PERSONS += _words("""
Hannah_Arendt Immanuel_Kant Georg_Wilhelm_Friedrich_Hegel Friedrich_Nietzsche Arthur_Schopenhauer Ludwig_Wittgenstein
Martin_Heidegger Karl_Popper Jürgen_Habermas Theodor_W._Adorno Max_Horkheimer Walter_Benjamin Ernst_Bloch Edith_Stein
Simone_de_Beauvoir Jean-Paul_Sartre Albert_Camus Michel_Foucault Pierre_Bourdieu Judith_Butler Niklas_Luhmann Max_Weber
Sigmund_Freud Anna_Freud Carl_Gustav_Jung Alfred_Adler Erich_Fromm Viktor_Frankl Jean_Piaget Lew_Wygotski Burrhus_F._Skinner
Iwan_Pawlow Abraham_Maslow Carl_Rogers Erik_Erikson Lawrence_Kohlberg Urie_Bronfenbrenner Howard_Gardner Daniel_Kahneman
Galileo_Galilei Nikolaus_Kopernikus Johannes_Kepler Gottfried_Wilhelm_Leibniz Carl_Friedrich_Gauß Max_Planck Werner_Heisenberg
Erwin_Schrödinger Niels_Bohr Otto_Hahn Robert_Koch Rudolf_Virchow Wilhelm_Conrad_Röntgen Ada_Lovelace Alan_Turing
Konrad_Zuse Stephen_Hawking Richard_Feynman Jane_Goodall Rachel_Carson Gregor_Mendel Louis_Pasteur Nikola_Tesla
Thomas_Edison Leonardo_da_Vinci Michelangelo Raffael Rembrandt Vincent_van_Gogh Claude_Monet Paul_Klee Wassily_Kandinsky
Franz_Marc August_Macke Gabriele_Münter Emil_Nolde Lyonel_Feininger Walter_Gropius Gottfried_Semper Matthäus_Daniel_Pöppelmann
Andy_Warhol Banksy Ai_Weiwei Marina_Abramović Pina_Bausch Marlene_Dietrich Romy_Schneider Hildegard_Knef Heinz_Rühmann
Charlie_Chaplin Marilyn_Monroe Audrey_Hepburn Meryl_Streep Leonardo_DiCaprio Tom_Hanks Sandra_Hüller Daniel_Brühl
Matthias_Schweighöfer Elyas_M'Barek Nora_Tschirner Moritz_Bleibtreu Christoph_Waltz Diane_Kruger Florian_Henckel_von_Donnersmarck
Wim_Wenders Werner_Herzog Rainer_Werner_Fassbinder Fritz_Lang Leni_Riefenstahl Steven_Spielberg Alfred_Hitchcock
Franz_Schubert Joseph_Haydn Antonio_Vivaldi Frédéric_Chopin Peter_Tschaikowski Gustav_Mahler Hildegard_von_Bingen
Kurt_Weill Hanns_Eisler Paul_Hindemith Karlheinz_Stockhausen Hans_Zimmer The_Beatles John_Lennon Paul_McCartney
Elvis_Presley Michael_Jackson Madonna Beyoncé Adele Ed_Sheeran Billie_Eilish Rihanna Lady_Gaga Bob_Dylan David_Bowie
Freddie_Mercury Nina_Hagen Marius_Müller-Westernhagen Wolf_Biermann Reinhard_Mey Rolf_Zuckowski Sarah_Connor
Mark_Forster Andreas_Bourani Clueso Cro Kraftklub Die_Ärzte Die_Toten_Hosen Rammstein Silbermond Tokio_Hotel Kraftwerk
Sido Bushido Capital_Bra Apache_207 Max_Giesinger Wincent_Weiss Vanessa_Mai Andrea_Berg Roland_Kaiser Florian_Silbereisen
Johann_Gutenberg Friedrich_der_Große Maria_Theresia Katharina_die_Große Elisabeth_I. Queen_Elizabeth König_Charles
Ludwig_XIV. Heinrich_VIII. Kolumbus Christoph_Kolumbus Ferdinand_Magellan Marco_Polo James_Cook Roald_Amundsen
Otto_Lilienthal Carl_Benz Gottlieb_Daimler Rudolf_Diesel Robert_Bosch Werner_von_Siemens Ferdinand_Porsche Carl_Zeiss
Ernst_Abbe Alfred_Krupp Steve_Jobs Bill_Gates Mark_Zuckerberg Jeff_Bezos Sam_Altman Warren_Buffett
Abraham_Lincoln George_Washington Thomas_Jefferson Franklin_D._Roosevelt Winston_Churchill Charles_de_Gaulle
Margaret_Thatcher Tony_Blair Boris_Johnson Rishi_Sunak Keir_Starmer Kamala_Harris Hillary_Clinton Bill_Clinton
George_W._Bush Ronald_Reagan Richard_Nixon Jimmy_Carter Justin_Trudeau Giorgia_Meloni Pedro_Sánchez Viktor_Orbán
Recep_Tayyip_Erdoğan Xi_Jinping Mao_Zedong Lenin Stalin Josef_Stalin Leo_Trotzki Fidel_Castro Che_Guevara
Jawaharlal_Nehru Indira_Gandhi Narendra_Modi Kofi_Annan Ban_Ki-moon António_Guterres Desmond_Tutu Wangari_Maathai
Václav_Havel Lech_Wałęsa Donald_Tusk Andrzej_Duda Emmanuel_Macron Jacques_Chirac François_Mitterrand Simone_Veil
Golda_Meir David_Ben-Gurion Jassir_Arafat Anwar_as-Sadat Mustafa_Kemal_Atatürk
Hans_und_Sophie_Scholl Claus_von_Stauffenberg Georg_Elser Dietrich_Bonhoeffer Oskar_Schindler Raoul_Wallenberg
Janusz_Korczak Elie_Wiesel Primo_Levi Victor_Klemperer Otto_Weidt Adolf_Hitler Joseph_Goebbels Heinrich_Himmler
Hermann_Göring Rudolf_Heß Erich_Mielke Egon_Krenz Günter_Schabowski Hans_Modrow Lothar_de_Maizière Wolf_Biermann
Bärbel_Bohley Rainer_Eppelmann Katrin_Göring-Eckardt Marianne_Birthler Roland_Jahn Gregor_Gysi Petra_Kelly
Hans-Dietrich_Genscher Helmut_Schmidt Walter_Scheel Ludwig_Erhard Kurt_Georg_Kiesinger Theodor_Heuss Gustav_Heinemann
Karl_Carstens Johannes_Rau Rita_Süssmuth Heiner_Geißler Franz_Josef_Strauß Edmund_Stoiber Horst_Seehofer Peter_Altmaier
Jens_Spahn Armin_Laschet Annegret_Kramp-Karrenbauer Ursula_Lehr Sigmar_Gabriel Martin_Schulz Andrea_Nahles Kevin_Kühnert
Oskar_Lafontaine Joschka_Fischer Jürgen_Trittin Claudia_Roth Anton_Hofreiter Omid_Nouripour Philipp_Rösler Guido_Westerwelle
Alice_Weidel Tino_Chrupalla Björn_Höcke Frauke_Petry Janine_Wissler Jan_van_Aken Ines_Schwerdtner Katja_Kipping
Stanislaw_Tillich Georg_Milbradt Kurt_Biedenkopf Sebastian_Gemkow Conrad_Clemens Wolfram_Günther
Katja_Meier Thomas_Schmidt Martin_Modschiedler Armin_Schuster Christiane_Schenderlein Frank_Richter Arnold_Vaatz
Helma_Orosz Ingolf_Roßberg Herbert_Wagner Eva_Jähnigen Jan_Donhauser Wolfgang_Tiefensee
Dieter_Bohlen Stefan_Raab Barbara_Schöneberger Helene_Fischer Florian_Silbereisen Kai_Pflaume Frank_Elstner Hans_Rosenthal
Rudi_Carrell Loriot Otto_Waalkes Dieter_Nuhr Gerhard_Polt Carolin_Kebekus Mario_Barth Kurt_Krömer Olaf_Schubert
Bernd_das_Brot Die_Sendung_mit_der_Maus Peter_Lustig Fritz_Fuchs Shary_Reeves Ralph_Caspers
Eva_Herman Ulrich_Wickert Tom_Buhrow Judith_Rakers Jens_Riewa Caren_Miosga Dunja_Hayali Maybrit_Illner Frank_Plasberg
Pinar_Atalay Linda_Zervakis Hajo_Seppelt Sascha_Lobo Rezo LeFloid Gronkh Julien_Bam Dagi_Bee Bibis_Beauty_Palace
Gerhard_Schröder Pele Pelé Diego_Maradona Lionel_Messi Cristiano_Ronaldo Kylian_Mbappé Erling_Haaland Zinédine_Zidane
Johan_Cruyff Gerd_Müller Uwe_Seeler Sepp_Maier Paul_Breitner Lothar_Matthäus Jürgen_Klinsmann Rudi_Völler
Philipp_Lahm Mesut_Özil Thomas_Tuchel Julian_Nagelsmann Hansi_Flick Joachim_Löw Pep_Guardiola José_Mourinho
Alexandra_Popp Lena_Oberdorf Almuth_Schult Birgit_Prinz Martina_Voss-Tecklenburg Giulia_Gwinn
Usain_Bolt Carl_Lewis Jesse_Owens Muhammad_Ali Mike_Tyson Wladimir_Klitschko Vitali_Klitschko Henry_Maske
Roger_Federer Rafael_Nadal Novak_Djoković Serena_Williams Venus_Williams Michael_Stich Andrea_Petković
Michael_Phelps Britta_Steffen Paul_Biedermann Kristin_Otto Heike_Drechsler Ulrike_Meyfarth Malaika_Mihambo Gina_Lückenkemper
Niklas_Kaul Johannes_Vetter Thomas_Röhler Christina_Schwanitz Claudia_Pechstein Anni_Friesinger Gunda_Niemann-Stirnemann
Felix_Loch Georg_Hackl Natalie_Geisenberger Francesco_Friedrich Eric_Frenzel Andreas_Wellinger Karl_Geiger Sven_Hannawald
Martin_Schmitt Kati_Wilhelm Uschi_Disl Ricco_Groß Denise_Herrmann-Wick Franziska_Preuß Arnd_Peiffer Benedikt_Doll
Viktoria_Rebensburg Maria_Höfl-Riesch Felix_Neureuther Max_Verstappen Lewis_Hamilton Niki_Lauda Ayrton_Senna
Mick_Schumacher Nico_Rosberg Jan_Ullrich Erik_Zabel Tony_Martin Emma_Hinze Kristina_Vogel 
Tim_Mälzer Johann_Lafer Horst_Lichter Tim_Raue Sarah_Wiener Frank_Rosin Nelson_Müller
Gudrun_Ensslin Ulrike_Meinhof Andreas_Baader Beate_Uhse Alice_Schwarzer Clara_Zetkin Louise_Otto-Peters Hedwig_Dohm
Helene_Lange Gertrud_Bäumer Marie_Juchacz Elisabeth_Selbert Bertha_von_Suttner Florence_Nightingale Mutter_Teresa
Hildegard_Hamm-Brücher Regine_Hildebrandt Christa_Wolf Brigitte_Reimann Irmtraud_Morgner Maxie_Wander Anna_Seghers
Stefan_Heym Heiner_Müller Ulrich_Plenzdorf Reiner_Kunze Jurek_Becker Thomas_Brussig Clemens_Meyer Lutz_Seiler
Nino_Haratischwili Olga_Grjasnowa Mithu_Sanyal Fatma_Aydemir Kübra_Gümüşay Deniz_Yücel Navid_Kermani Feridun_Zaimoglu
Emine_Sevgi_Özdamar Yoko_Tawada Terézia_Mora Jenny_Erpenbeck Judith_Hermann Benedict_Wells Marc-Uwe_Kling Timur_Vermes
Frank_Schätzing Wolfgang_Hohlbein Andreas_Eschbach Kerstin_Gier Cornelia_Franz Andreas_Steinhöfel Finn-Ole_Heinrich
Nils_Mohl Susan_Kreller Kirsten_Fuchs Martina_Wildner Jutta_Richter Peter_Härtling Mirjam_Pressler Klaus_Kordon
Uwe_Timm Christoph_Ransmayr Peter_Handke Elfriede_Jelinek Thomas_Bernhard Ingeborg_Bachmann Paul_Celan Rose_Ausländer
Else_Lasker-Schüler Nelly_Sachs Mascha_Kaléko Kurt_Tucholsky Erich_Mühsam Carl_Zuckmayer Stefan_Zweig Joseph_Roth
Alfred_Döblin Hans_Fallada Irmgard_Keun Lion_Feuchtwanger Klaus_Mann Heinrich_Mann Rainer_Maria_Rilke Stefan_George
Georg_Trakl Gottfried_Benn Georg_Heym August_Stramm Eduard_Mörike Joseph_von_Eichendorff Novalis Friedrich_Hölderlin
Heinrich_von_Kleist E._T._A._Hoffmann Annette_von_Droste-Hülshoff Gottfried_Keller Theodor_Storm Wilhelm_Raabe
Gerhart_Hauptmann Arthur_Schnitzler Hugo_von_Hofmannsthal Frank_Wedekind Georg_Büchner Christian_Fürchtegott_Gellert
Friedrich_Gottlieb_Klopstock Johann_Gottfried_Herder Christoph_Martin_Wieland Karl_May Wilhelm_Hauff Hans_Christian_Andersen
William_Shakespeare Charles_Dickens Jane_Austen Virginia_Woolf George_Orwell Aldous_Huxley J._K._Rowling J._R._R._Tolkien
C._S._Lewis Roald_Dahl Mark_Twain Ernest_Hemingway Toni_Morrison Margaret_Atwood Agatha_Christie Arthur_Conan_Doyle
Leo_Tolstoi Fjodor_Dostojewski Anton_Tschechow Alexander_Puschkin Victor_Hugo Gustave_Flaubert Molière Dante_Alighieri
Homer Sophokles Aristoteles Platon Sokrates Cicero Seneca Konfuzius Buddha Mohammed Jesus Moses Abraham
Gabriel_García_Márquez Isabel_Allende Paulo_Coelho Haruki_Murakami Orhan_Pamuk Chimamanda_Ngozi_Adichie Annie_Ernaux
Ronja_von_Rönne Sophie_Passmann Margarete_Stokowski Joachim_Meyerhoff Daniela_Krien Dörte_Hansen Mariana_Leky Juli_Zeh
Luisa_Neubauer Carola_Rackete Malu_Dreyer Franziska_Giffey Kai_Wegner Klaus_Wowereit Peter_Tschentscher Ole_von_Beust
Reiner_Haseloff Dietmar_Woidke Mario_Voigt Michael_Kretschmer Daniel_Günther Stephan_Weil Olaf_Lies Boris_Rhein
Alexander_Schweitzer Anke_Rehlinger Andreas_Bovenschulte Ilse_Aigner Hubert_Aiwanger Alexander_Dobrindt Markus_Blume
Steffi_Lemke Lisa_Paus Svenja_Schulze Olaf_Scholz Klara_Geywitz Volker_Wissing Marco_Buschmann Christian_Lindner
Johann_Wadephul Karin_Prien Dorothee_Bär Katherina_Reiche Thorsten_Frei Lars_Klingbeil Bärbel_Bas Carsten_Linnemann
Papst_Benedikt Papst_Johannes_Paul_II. Margot_Käßmann Heinrich_Bedford-Strohm Rainer_Maria_Woelki Reinhard_Marx
Weihnachtsmann Christkind Osterhase Sandmännchen Unser_Sandmännchen Pittiplatsch Schnatterinchen Moppi Herr_Fuchs Frau_Elster
Alfred_Jodocus_Kwak Bob_der_Baumeister Peppa_Wutz Paw_Patrol Feuerwehrmann_Sam Thomas_die_kleine_Lokomotive Elsa Anna_und_Elsa
Olaf_der_Schneemann Simba Nemo Dory Shrek Mickey_Maus Donald_Duck Dagobert_Duck Micky_Maus Goofy Pluto Minnie_Maus
SpongeBob Batman Superman Spider-Man Wonder_Woman Iron_Man Hulk Darth_Vader Luke_Skywalker Yoda Frodo Gandalf
Bilbo_Beutlin Ron_Weasley Albus_Dumbledore Lord_Voldemort Severus_Snape Hagrid Katniss_Everdeen Bella_Swan
Alice_im_Wunderland Peter_Pan Captain_Hook Robin_Hood Don_Quijote Faust Mephisto Gretchen Werther Nathan Effi
Tom_Buddenbrook Hans_Castorp Oskar_Matzerath Josef_K. Gregor_Samsa Momo Bastian_Balthasar_Bux Atreju Fuchur
Kater_Mikesch Urmel Wum_und_Wendelin Nils_Holgersson Heidi Peter_und_der_Wolf Hatschi_Bratschi Rübezahl Till_Eulenspiegel
Baron_Münchhausen Rapunzel Schneewittchen Aschenputtel Dornröschen Frau_Holle Der_gestiefelte_Kater Die_Bremer_Stadtmusikanten
Tim_und_Struppi Asterix Obelix Lucky_Luke Garfield Snoopy Charlie_Brown Calvin_und_Hobbes Fix_und_Foxi Die_Schlümpfe
Sendung_mit_der_Maus Die_Maus Der_Elefant Ernie_und_Bert Krümelmonster Elmo Bibo Samson Tiffy Grobi Kermit Miss_Piggy
Lassie Flipper Fury Black_Beauty Ostwind Wendy Bibi_und_Tina Die_wilden_Kerle Die_wilden_Hühner TKKG-Bande
Justus_Jonas Peter_Shaw Bob_Andrews Kommissar_Rex Derrick Columbo Miss_Marple Hercule_Poirot James_Bond Indiana_Jones
""")

PUBLIC_PERSONS = list(dict.fromkeys(PUBLIC_PERSONS))

#: Even more cited researchers (education, psychology, sociology, didactics, methods), from Germany and abroad.
AUTHORS += _words("""
Bronfenbrenner Erikson Kohlberg Gilligan Maslow Rogers Skinner Pawlow Thorndike Gagné Ausubel Bruner Leontjew Galperin
Engeström Lave Wenger Schön Argyris Senge Weick Giddens Bauman Elias Durkheim Simmel Goffman Garfinkel Schütz Luckmann
Mannheim Honneth Reckwitz Nassehi Lessenich Butterwegge Hradil Geißler Solga Blossfeld Allmendinger Esser Boudon Coleman
Putnam Lareau Bernstein Willis Giroux McLaren Ladson-Billings Nieto Cummins Krashen Swain Gibbons Feilke Ossner Spinner
Kammler Frederking Kepser Rosebrock Nix Lewin Moreno Watzlawick Schulz_von_Thun Satir Bateson Marzano Danielson Decristan
Rakoczy Shavelson Messick Cronbach Kane Rasch Embretson Muthén Bollen Kline Hox Raudenbush Snijders Bosker Cohen Field
Hattie Timperley Wiliam Csikszentmihalyi Vygotskij Wygotskij Foucault Butler Crenshaw Mead Habermas Arendt Fromm Freud
Weinert Helmke Klieme Baumert Kunter Blömeke Terhart Lipowsky Seidel Reusser Pauli Hugener Krammer Sturm Wiesmann
Hellmich Feindt Kunze Rabe-Kleberg Fried Roßbach Tietze Anders Kluczniok Weinert Ebert Grgic Sylva Melhuish Siraj
Pramling Samuelsson Dahlberg Moss Rinaldi Malaguzzi Laewen Andres Schäfer Leu Viernickel Fröhlich-Gildhoff Nentwig-Gesemann
Wolters Ainsworth Bowlby Grossmann Brisch Spitz Winnicott Stern Papoušek Largo Juul Gordon Dreikurs Haim_Omer Rosenberg
Fend Ditton Bos Lankes Valtin Schründer-Lenzen Hornberg Gogolin Lange Kniffka Siebert-Ott Rösch Jeuk Schmölzer-Eibinger
Müller-Using Schlag Brügelmann Bartnitzky Röhner Kahlert Gläser Hartinger Möller Giest Pech Lauterbach Kaiser Michalik
Wittmann Hengartner Krauthausen Scherer Padberg Wittmann Müller Wollring Selter Spiegel Schipper Kaufmann Radatz
Leuders Barzel Prediger Büchter Hußmann Vollrath Kirsch Winter Freudenthal Polya Wagenschein Duit Labudde Gräber Neumann
Fischer Kauertz Hopf Wilhelm Euler Mikelskis-Seifert Muckenfuß Rincke Schecker Sumfleth Bayrhuber Gropengießer Kattmann
Reinfried Haubrich Otto Hemmer Rinschede Schrüfer Gryl Budke Kuckuck Lambert Lehner Hoffmann Ohl Rhode-Jüchtern
Pandel Rüsen Jeismann Körber Schreiber Gautschi Bergmann Borries Sauer Barricelli Lücke Thünemann Kühberger Ammerer
Sander Weißeno Detjen Massing Kohlberg Reinhardt Grammes Hedtke Juchler Petrik Besand Oberle Manzel Gessner
Rösch Stange Wolff Klippert Meyer Gudjons Jank Kron Peterßen Schröder Bönsch Kiper Mischke Lehner Bohl Kleinknecht
Schratz Steiner Rolff Fullan Bonsen Pietsch Tulowitzki Schratz Huber Wissinger Brauckmann Muslic Warwas Klein Dutke
Hasselhorn Gold Schneider Lauth Grünke Ahrbeck Hinz Wocken Feuser Reich Sander Prengel Hinz Boban Werning Lütje-Klose
Moser Opalinski Heimlich Kracke Bless Hillenbrand Casale Hennemann Urton Stein Müller Myschker Ellinger Ricking
Klemm Rauschenbach Züchner Thole Böllert Otto Thiersch Böhnisch Schröer Sting Hamburger Winkler Mollenhauer Wolff
Kraus Rothland Klusmann Lüders Hericks Keller-Schneider Schaarschmidt Fischer Kieschke Rothland Bauer Holzberger
Kunina-Habenicht Lohse-Bossenz König Kaiser Krauss Blum Neubrand Brunner Jordan Kleickmann Richter Voss Tröbst
Dicke Kleickmann Rjosk Leutner Wirth Renkl Atkinson Chi Mayer Sweller Chandler Paas van_Merriënboer Kirschner Clark
Hmelo-Silver Kapur Schwartz Bransford Brown Collins Palincsar Scardamalia Bereiter Stahl Fischer Weinberger Kollar
Zimmerman Schunk Pintrich Boekaerts Winne Hadwin Efklides Schmitz Landmann Perels Dignath Büttner Otto Spörer
Artelt Stanat Schneider Schiefele Retelsdorf Köller Möller Marsh Trautwein Lüdtke Nagengast Preckel Brunner Ziegler
Stern Grabner Neubauer Schrader Helmke Brühwiler Praetorius Rieser Hamre Downer Allen Pianta Reyes Brackett Jennings
Greenberg Durlak Weissberg Zins Elias Roeser Eccles Wigfield Midgley Ames Nicholls Elliot Dweck Murayama Pekrun Frenzel
Goetz Götz Hall Perry Linnenbrink-Garcia Schutz Lazarus Gross Hascher Hagenauer Tillmann Eder Fend Grob Kiper
Kuhl Heckhausen Rheinberg Vollmeyer Brandstätter Schüler Gollwitzer Oettingen Bandura Schwarzer Jerusalem Satow
""")
AUTHORS = list(dict.fromkeys(a for a in AUTHORS))

#: Book, film and song titles, also with first names in them.
WORKS += _words("""
Der_Herr_der_Ringe Der_Hobbit Die_Tribute_von_Panem Twilight Gregs_Tagebuch Die_Schule_der_magischen_Tiere Lotta-Leben
Die_Olchis Das_Sams Drachenreiter Die_Wilden_Hühner Rico,_Oskar_und_das_Herzgebreche Ben_liebt_Anna Die_Brücke
Das_fliegende_Klassenzimmer Pünktchen_und_Anton Konferenz_der_Tiere Die_Konferenz_der_Tiere Der_kleine_Wassermann
Das_kleine_Gespenst Die_Häschenschule Oh,_wie_schön_ist_Panama Der_Grüffelo Die_kleine_Raupe_Nimmersatt Frederick
Wo_die_wilden_Kerle_wohnen Das_Neinhorn Der_Regenbogenfisch Elmar Die_Torte_ist_weg Vom_kleinen_Maulwurf
Paul_und_Paula Die_Legende_von_Paul_und_Paula Good_Bye,_Lenin! Das_Leben_der_Anderen Sonnenallee Lola_rennt
Der_Untertan Die_Blechtrommel Im_Westen_nichts_Neues Der_Schimmelreiter Der_Process Die_Verwandlung Das_Parfum
Der_Turm Kruso Unterleuten Corpus_Delicti Die_Physiker Der_Besuch_der_alten_Dame Andorra Homo_faber Der_Richter_und_sein_Henker
Mutter_Courage Der_gute_Mensch_von_Sezuan Die_Dreigroschenoper Biedermann_und_die_Brandstifter Leonce_und_Lena Dantons_Tod
Minna_von_Barnhelm Emilia_Galotti Die_Räuber Don_Carlos Wallenstein Der_zerbrochne_Krug Prinz_Friedrich_von_Homburg
Der_Hauptmann_von_Köpenick Berlin_Alexanderplatz Kleiner_Mann_–_was_nun? Jeder_stirbt_für_sich_allein Das_kunstseidene_Mädchen
Schachnovelle Die_Welt_von_Gestern Radetzkymarsch Der_Zauberberg Tonio_Kröger Der_Tod_in_Venedig Mario_und_der_Zauberer
Unterm_Rad Narziß_und_Goldmund Demian Das_Glasperlenspiel Der_Hundertjährige,_der_aus_dem_Fenster_stieg_und_verschwand
Die_Känguru-Chroniken Er_ist_wieder_da Der_Schwarm Tintenblut Tintentod Reckless Rubinrot Saphirblau Smaragdgrün
Woyzeck Lenz Hiob Jakob_der_Lügner Die_neuen_Leiden_des_jungen_W. Der_geteilte_Himmel Kassandra Medea Nachdenken_über_Christa_T.
Max_und_die_wilde_7 Ein_Mann_namens_Ove Wunder Das_Schicksal_ist_ein_mieser_Verräter Tschick Bilder_deiner_großen_Liebe
Sophies_Welt Der_Alchimist Der_Fänger_im_Roggen 1984 Farm_der_Tiere Schöne_neue_Welt Fahrenheit_451 Wer_die_Nachtigall_stört
Oliver_Twist Robinson_Crusoe Gullivers_Reisen Die_Schatzinsel Moby_Dick Krieg_und_Frieden Anna_Karenina Schuld_und_Sühne
Madame_Bovary Die_Elenden Der_Glöckner_von_Notre-Dame Romeo_und_Julia Hamlet Macbeth König_Lear Ein_Sommernachtstraum
Die_Zauberflöte Don_Giovanni Figaros_Hochzeit Fidelio Der_Freischütz Carmen Hänsel_und_Gretel Peter_und_der_Wolf
Die_vier_Jahreszeiten Für_Elise Ode_an_die_Freude Stille_Nacht Alle_meine_Entchen Atemlos_durch_die_Nacht 99_Luftballons
Über_sieben_Brücken_musst_du_gehn Männer Bochum Major_Tom Ein_Kompliment Wind_of_Change Westerland Junge Tage_wie_diese
Der_Pate Titanic Star_Wars Frozen Die_Eiskönigin Der_König_der_Löwen Findet_Nemo Toy_Story Shrek Barbie Oppenheimer
Drei_Haselnüsse_für_Aschenbrödel Das_singende,_klingende_Bäumchen Die_Geschichte_vom_kleinen_Muck Das_kalte_Herz
Bildung_und_Gerechtigkeit Visible_Learning Lernen_sichtbar_machen Allgemeinbildung_und_Lehrerausbildung Die_Ordnung_des_Diskurses
Das_Kapital Das_Kommunistische_Manifest Kritik_der_reinen_Vernunft Also_sprach_Zarathustra Vita_activa Eichmann_in_Jerusalem
Emile Der_Gesellschaftsvertrag Wie_Gertrud_ihre_Kinder_lehrt Pädagogik_der_Unterdrückten Didaktische_Analyse Was_ist_guter_Unterricht?
""")
WORKS = list(dict.fromkeys(WORKS))

#: More places that appear without personal reference (news, travel, history, conferences).
PUBLIC_PLACES += _words("""
Amerika USA Vereinigte_Staaten Russland Ukraine Belarus Litauen Lettland Ungarn Rumänien Bulgarien Griechenland Türkei
Syrien Irak Iran Afghanistan Israel Palästina Gaza Ägypten Marokko Tunesien Nigeria Ghana Kenia Südafrika Brasilien
Argentinien Mexiko Chile Peru Indien Pakistan Vietnam Thailand Indonesien Korea Südkorea Nordkorea Australien Neuseeland
Island Irland Schottland Wales Niederlande Holland Belgien Luxemburg Portugal Kroatien Serbien Slowenien Slowakei
Afrika Asien Südamerika Nordamerika Antarktis Arktis Ostsee Nordsee Mittelmeer Atlantik Alpen Harz Rügen Sylt Usedom
Brocken Zugspitze Fichtelberg Lilienstein Bastei Elbsandsteingebirge Oberlausitz Mittelsachsen Westsachsen Nordsachsen
Leipziger_Land Muldental Osterzgebirge Zittauer_Gebirge Spreewald Ostdeutschland Westdeutschland Mitteldeutschland
DDR BRD Sowjetunion Preußen Bundesrepublik New_York Washington Los_Angeles Chicago San_Francisco Tokio Peking Shanghai
Moskau Sankt_Petersburg Warschau Budapest Athen Madrid Barcelona Lissabon Amsterdam Kopenhagen Stockholm Oslo Helsinki
Dublin Edinburgh Venedig Florenz Mailand Neapel Genf Basel Bern Salzburg Innsbruck Graz Straßburg Lyon Marseille
Auschwitz Buchenwald Theresienstadt Nürnberg Wittenberg Eisenach Wartburg Sanssouci Torgau Hiroshima Tschernobyl
Elbe Moldau Rhein Donau Spree Neiße Mulde Saale Weißeritz Oder Havel Main Mosel Neckar Isar Weser Ems
Dresden-Altstadt Neumarkt Altmarkt Brühlsche_Terrasse Zwinger Theaterplatz Prager_Straße Großer_Garten Augustusbrücke
Blaues_Wunder Elbwiesen Pillnitz Moritzburg Radebeul Meißen Leipzig-Mitte Völkerschlachtdenkmal Augustusplatz
""")
PUBLIC_PLACES = list(dict.fromkeys(PUBLIC_PLACES))

#: Publishers and places of publication for literature references ("Weinheim: Beltz").
PUBLISHERS = _words("""
Weinheim:_Beltz Weinheim_und_Basel:_Beltz Bad_Heilbrunn:_Klinkhardt Münster:_Waxmann Wiesbaden:_Springer_VS
Opladen:_Barbara_Budrich Stuttgart:_Kohlhammer Göttingen:_Hogrefe Berlin:_Cornelsen Seelze:_Kallmeyer Hamburg:_Rowohlt
München:_Reinhardt Bielefeld:_transcript Frankfurt_am_Main:_Suhrkamp Baltmannsweiler:_Schneider_Hohengehren
Paderborn:_Schöningh Tübingen:_Narr Heidelberg:_Springer Berlin:_de_Gruyter Freiburg:_Herder London:_Routledge
New_York:_Routledge Thousand_Oaks:_Sage Oxford:_Oxford_University_Press Cambridge:_Cambridge_University_Press
""")

#: Words for invented titles of papers and books ("Feedback im Mathematikunterricht der Grundschule").
TITLE_HEADS = _words("""
Feedback Klassenführung Unterrichtsqualität Lernmotivation Leseförderung Inklusion Heterogenität Digitalisierung
Professionalisierung Lehrkräftebildung Schulentwicklung Bildungsungleichheit Mehrsprachigkeit Sprachbildung
Selbstreguliertes_Lernen Kooperatives_Lernen Ganztagsschule Elternarbeit Übergänge Schulerfolg Leistungsbeurteilung
Kompetenzorientierung Diagnostik Förderdiagnostik Lernstörungen Schulangst Wohlbefinden Belastung Beanspruchung
Partizipation Demokratiebildung Medienbildung Bildungsgerechtigkeit Frühe_Bildung Sprachförderung Kita-Qualität
""")
TITLE_TAILS = _words("""
in_der_Grundschule im_Mathematikunterricht im_Deutschunterricht in_der_Sekundarstufe_I in_inklusiven_Klassen
im_Lehramtsstudium in_der_Kita an_Oberschulen im_internationalen_Vergleich –_eine_Längsschnittstudie
–_Befunde_einer_Videostudie –_ein_Überblick :_Theorie,_Empirie,_Praxis aus_Sicht_der_Lehrkräfte bei_Kindern_mit_Fluchterfahrung
und_ihre_Bedeutung_für_den_Lernerfolg im_Wandel :_Eine_Metaanalyse in_Sachsen in_Deutschland
""")
JOURNALS = _words("""
Zeitschrift_für_Pädagogik Zeitschrift_für_Erziehungswissenschaft Unterrichtswissenschaft Zeitschrift_für_Pädagogische_Psychologie
Journal_for_Educational_Research_Online Learning_and_Instruction Journal_of_Educational_Psychology Die_Deutsche_Schule
Zeitschrift_für_Grundschulforschung Empirische_Pädagogik Psychologie_in_Erziehung_und_Unterricht Frühe_Bildung
Review_of_Educational_Research Teaching_and_Teacher_Education Zeitschrift_für_Bildungsforschung
""")


def surname_of(person: str) -> str | None:
    """The surname of a public person ("Angela Merkel" → "Merkel"), None for single names and figures."""
    parts = person.split()
    if len(parts) < 2 or any(w in parts for w in ("und", "der", "die", "das", "vom", "aus", "im")) or not parts[-1][:1].isupper():
        return None
    return parts[-1]


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


#: Public institutions that carry a place ("Universitätsklinikum Dresden", "Stadtbibliothek Pirna").
_PUBLIC_WITH_PLACE = _words("""
Universitätsklinikum Klinikum Krankenhaus Stadtbibliothek Volkshochschule Landratsamt Rathaus Hauptbahnhof Zoo
Staatstheater Stadtmuseum Kunsthalle Stadtwerke Arbeitsagentur Amtsgericht Landgericht Polizeidirektion Universität
Hochschule Technische_Universität Stadtarchiv Staatsarchiv Landesbühnen Philharmonie Planetarium Tierpark Stadtverwaltung
Bürgeramt Finanzamt Sparkasse Verkehrsbetriebe Messe Kreisverwaltung Stadtrat Kreistag Landtag Stadthalle Sternwarte
""")


def public_institution(rng: random.Random) -> str:
    """A public institution – from the list or with a place ("Stadtbibliothek Freital", "Universität Leipzig")."""
    if rng.random() < 0.5:
        return rng.choice(PUBLIC_INSTITUTIONS)
    where = rng.choice(PUBLIC_PLACES) if rng.random() < 0.5 else place(rng)
    return f"{rng.choice(_PUBLIC_WITH_PLACE)} {where}"


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
