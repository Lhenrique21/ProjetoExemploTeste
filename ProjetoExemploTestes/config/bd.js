const Sequelize = require('sequelize');

const sequelize = new Sequelize({
    dialect: 'sqlite',
    storage: process.env.BD_STORAGE || './bd.sqlite'
});

module.exports = sequelize;